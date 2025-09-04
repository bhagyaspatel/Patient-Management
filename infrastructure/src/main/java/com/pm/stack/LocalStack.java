package com.pm.stack;

import software.amazon.awscdk.*;

public class LocalStack extends Stack {

    /*
    A stack is a collection of AWS resources (EC2, RDS, S3, VPC, IAM roles, etc.) that you create, manage, and delete as a single unit.
    You define the resources in a CloudFormation template (YAML/JSON).
    When you deploy that template, AWS CloudFormation provisions all the resources together → that deployment is called a stack
    */
    public LocalStack(final App scope, final String id, final StackProps props){
        super(scope, id, props);
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
