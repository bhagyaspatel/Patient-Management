package com.pm.stack;

import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.*;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.msk.CfnCluster;
import software.amazon.awscdk.services.rds.*;
import software.amazon.awscdk.services.route53.CfnHealthCheck;

import java.util.stream.Collectors;

public class LocalStack extends Stack {
    private final Vpc vpc;
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

        DatabaseInstance patientServiceDb = createDatabase("AuthServiceDB", "auth-service-db");

        CfnHealthCheck patientDbHealthCheck = createDbHealthCheck(patientServiceDb, "PatientServiceDbHealthCheck");

        CfnCluster mskCluster = createMskCluster();
    }

    private Vpc createVpc() {
         return Vpc.Builder
                 .create(this, "PatientManagementVpc")
                 .vpcName("PatientManagementVpc")
                 .maxAzs(2) // maximum availability zones
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
