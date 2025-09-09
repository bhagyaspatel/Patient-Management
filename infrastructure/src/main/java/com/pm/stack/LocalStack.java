package com.pm.stack;

import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.*;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.ecs.Protocol;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.msk.CfnCluster;
import software.amazon.awscdk.services.rds.*;
import software.amazon.awscdk.services.route53.CfnHealthCheck;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class LocalStack extends Stack {
    private final Vpc vpc;

    private final Cluster ecsCluster;

    private final String API_GATEWAY_IMAGE_NAME = "api-gateway";

    /*
    A stack is a collection of AWS resources (EC2, RDS, S3, VPC, IAM roles, etc.) that you create, manage, and delete as a single unit.
    You define the resources in a CloudFormation template (YAML/JSON).
    When you deploy that template, AWS CloudFormation provisions all the resources together → that deployment is called a stack
    */
    public LocalStack(final App scope, final String id, final StackProps props){
        super(scope, id, props);
        
        this.vpc = createVpc();

        DatabaseInstance authServiceDb = createDatabase("AuthServiceDB", "auth-service-db");

        CfnHealthCheck authDbHealthCheck = createDbHealthCheck(authServiceDb, "AuthServiceDbHealthCheck");

        DatabaseInstance patientServiceDb = createDatabase("Patient ServiceDB", "patient-service-db");

        CfnHealthCheck patientDbHealthCheck = createDbHealthCheck(patientServiceDb, "PatientServiceDbHealthCheck");

        CfnCluster mskCluster = createMskCluster();

        this.ecsCluster = createEcsCluster();

        // Auth-Service
        FargateService authService =
                createFargateService("AuthService",
                "auth-service",
                        List.of(4005),
                        authServiceDb,
                        Map.of("JWT_SECRET", "fab944774cee4ca19a48f224a416b414575e6791d30dd7beeb7b3baa520c62fc"));

        authService.getNode().addDependency(authServiceDb); // this ensures that authServiceDb is started before authService is start
        authService.getNode().addDependency(authDbHealthCheck);

        // Billing-Service
        FargateService billingService =
                createFargateService("BillingService",
                        "billing-service",
                        List.of(4001, 9001),
                        null,
                        null);

        // Analytics-Service
        FargateService analyticsService =
                createFargateService("AnalyticsService",
                        "analytics-service",
                        List.of(4002),
                        null,
                        null);

        analyticsService.getNode().addDependency(mskCluster);

        // Patient-Service
        FargateService patientService =
                createFargateService("PatientService",
                        "patient-service",
                        List.of(4000),
                        patientServiceDb,
                        Map.of(
                                "BILLING_SERVICE_ADDRESS", "host.docker.internal",
                                "BILLING_SERVICE_GRPC_PORT", "9001"
                        ));

        patientService.getNode().addDependency(patientServiceDb);
        patientService.getNode().addDependency(patientDbHealthCheck);
        patientService.getNode().addDependency(billingService);
        patientService.getNode().addDependency(mskCluster);

        createApiGatewayService();
    }

    private Vpc createVpc() {
         return Vpc.Builder
                 .create(this, "PatientManagementVpc")
                 .vpcName("PatientManagementVpc")
                 .maxAzs(2) // maximum availability zones
                 .build();
    }

    // to find a service inside our cluster we can do: auth-service.<cluster-namespace>: ie. auth-service.patient-management.local
    private Cluster createEcsCluster(){
         return Cluster.Builder.create(this, "PatientManagementCluster")
                 .vpc(vpc)
                 .defaultCloudMapNamespace(CloudMapNamespaceOptions.builder().name("patient-management.local").build())
                 .build();
    }

    private FargateService createFargateService(String id, String imageName, List<Integer> ports, DatabaseInstance db, Map<String, String> additionalEnvVars){
        // Each ECS service has ECS task running inside it
        FargateTaskDefinition taskDefinition =
                FargateTaskDefinition.Builder.create(this, id + "Task")
                .cpu(256)
                .memoryLimitMiB(512)
                        .build();

        ContainerDefinitionOptions.Builder containerOptionsBuilder =
                ContainerDefinitionOptions.builder()
                        .image(ContainerImage.fromRegistry(imageName)) // for localstack it will know to where to pull our local image from
                        .portMappings(ports.stream()
                                .map(port -> PortMapping.builder()
                                        .containerPort(port)
                                        .hostPort(port)
                                        .protocol(Protocol.TCP)
                                        .build())
                                .toList()
                        )
                        .logging(LogDriver.awsLogs(AwsLogDriverProps.builder()
                                .logGroup(LogGroup.Builder.create(this, id + "LogGroup")
                                        .logGroupName("/ecs/" + imageName)
                                        .removalPolicy(RemovalPolicy.DESTROY)
                                        .retention(RetentionDays.ONE_DAY)
                                        .build()
                                )
                                        .streamPrefix(imageName)
                                .build())
                        );

        Map<String, String> envVars = new HashMap<>();
        envVars.put("SPRING_KAFKA_BOOTSTRAP_SERVER", "localhost.localstack.cloud:4510, localhost.localstack.cloud:4511, localhost.localstack.cloud:4512");

        if(Objects.nonNull(additionalEnvVars)){
            envVars.putAll(additionalEnvVars);
        }

        if(Objects.nonNull(db)){
            envVars.put("SPRING_DATASOURCE_URL", "jdbc:postgresql://%s:%s/%s-db".formatted(
                    db.getDbInstanceEndpointAddress(),
                    db.getDbInstanceEndpointPort(),
                    imageName
            ));
            envVars.put("SPRING_DATASOURCE_USERNAME", "admin");

            // whenever we create the db, the TDK will create a pwd behind the scene and add it to secrets manager
            envVars.put("SPRING_DATASOURCE_PASSWORD", db.getSecret().secretValueFromJson("password").toString());

            envVars.put("SPRING_JPA_HIBERNATE_DDL_AUTO", "update");
            envVars.put("SPRING_SQL_INIT_MODE", "always");
            envVars.put("SPRING_DATASOURCE_HIKARI_INITIALIZATION_FAIL_TIMEOUT", "60000 "); // timeout if db fails, it tries few times before throwing error
        }

        containerOptionsBuilder.environment(envVars);

        taskDefinition.addContainer(imageName + "Container", containerOptionsBuilder.build());

        return FargateService.Builder.create(this, id)
                .cluster(ecsCluster)
                .taskDefinition(taskDefinition)
                .assignPublicIp(false) // not exposing the service to the internet
                .serviceName(imageName)
                .build();
    }

    private void createApiGatewayService(){
        FargateTaskDefinition taskDefinition =
                FargateTaskDefinition.Builder.create(this, "ApiGatewayTask")
                        .cpu(256)
                        .memoryLimitMiB(512)
                        .build();

        ContainerDefinitionOptions containerOptions =
                ContainerDefinitionOptions.builder()
                        .image(ContainerImage.fromRegistry(API_GATEWAY_IMAGE_NAME)) // for localstack it will know to where to pull our local image from
                        .environment(Map.of(
                                "SPRING_PROFILES_ACTIVE", "prod",
                                "AUTH_SERVICE_URL", "http://host.docker.internal:4005" //localstack does not implement service discover very well, so we are going to use docker internal service discovery
                        ))
                        .portMappings(List.of(4004).stream()
                                .map(port -> PortMapping.builder()
                                        .containerPort(port)
                                        .hostPort(port)
                                        .protocol(Protocol.TCP)
                                        .build())
                                .toList()
                        )
                        .logging(LogDriver.awsLogs(AwsLogDriverProps.builder()
                                .logGroup(LogGroup.Builder.create(this,   "ApiGatewayLogGroup")
                                        .logGroupName("/ecs/" + API_GATEWAY_IMAGE_NAME)
                                        .removalPolicy(RemovalPolicy.DESTROY)
                                        .retention(RetentionDays.ONE_DAY)
                                        .build()
                                )
                                .streamPrefix(API_GATEWAY_IMAGE_NAME)
                                .build())
                        )
                        .build();

        taskDefinition.addContainer("ApiGatewayContianer", containerOptions);

        ApplicationLoadBalancedFargateService apigateway =
                ApplicationLoadBalancedFargateService.Builder.create(this, "ApiGatewayService")
                        .cluster(ecsCluster)
                        .serviceName(API_GATEWAY_IMAGE_NAME)
                        .taskDefinition(taskDefinition)
                        .desiredCount(1)
                        .healthCheckGracePeriod(Duration.seconds(60))
                        .build();
    }

    private DatabaseInstance createDatabase(String id, String dbname){
        return DatabaseInstance.Builder
                .create(this, id)
                .engine(DatabaseInstanceEngine.postgres(
                        PostgresInstanceEngineProps
                                .builder()
                                .version(PostgresEngineVersion.VER_17_2)
                                .build()
                ))
                .vpc(vpc)
                .instanceType(InstanceType.of(InstanceClass.BURSTABLE2, InstanceSize.MICRO)) // specifies the CPU, storage power etc. For localstack does not matter much
                .allocatedStorage(20)
                .credentials(Credentials.fromGeneratedSecret("admin"))
                .databaseName(dbname)
                .removalPolicy(RemovalPolicy.DESTROY) // so each time we destroy the stack, the data is destroyed, good for development purposes
                .build();
    }

    private CfnHealthCheck createDbHealthCheck(DatabaseInstance db, String id){
        return CfnHealthCheck.Builder.create(this, id)
                .healthCheckConfig(CfnHealthCheck.HealthCheckConfigProperty.builder()
                        .type("TCP")
                        .port(Token.asNumber(db.getDbInstanceEndpointPort()))
                        .ipAddress(db.getDbInstanceEndpointAddress())
                        .requestInterval(30) // checks db health every 30 seconds
                        .failureThreshold(3)
                        .build()
                )
                .build();
    }

    private CfnCluster createMskCluster(){
        return CfnCluster.Builder.create(this, "MskCluster")
                .clusterName("kafka-cluster")
                .kafkaVersion("2.8.0")
                .numberOfBrokerNodes(1)
                .brokerNodeGroupInfo(CfnCluster.BrokerNodeGroupInfoProperty.builder()
                        .instanceType("kakfa.5.xlarge")
                        .clientSubnets(vpc.getPrivateSubnets().stream().map(ISubnet::getSubnetId).collect(Collectors.toList()))
                        .brokerAzDistribution("DEFAULT") // home is broker distribution across our Availability zones
                        .build()
                )
                .build();
    }

    public static void main(String[] args) {
        App app = new App(AppProps.builder().outdir("./cdk.out").build());
        // when our stack is created, it will create our cloudFromation template in ./cdk.out dire

        StackProps props = StackProps.builder()
                .synthesizer(new BootstraplessSynthesizer()) // synthesizer converts our code into cloudFormation template
                .build();
        // BootstraplessSynthesizer specifies to skip initial bootstraping of cdk environment, as we don't need it for our localstack

        new LocalStack(app, "localstack", props);
        app.synth(); //that we want to take our stack and props, convert it to cloudFormation template put everything into ./cdk.out folder

        System.out.println("App synthesizing in progress..");
    }
}
