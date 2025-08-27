package com.pm.grpc;

import billing.BillingRequest;
import billing.BillingResponse;
import billing.BillingServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.hibernate.service.spi.InjectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Service
public class BillingServiceGrpcClient {
    private final BillingServiceGrpc.BillingServiceBlockingStub blockingStub;

    private static final Logger log = LoggerFactory.getLogger(BillingServiceGrpcClient.class);

    // gRPC server endpoint defined in proto: localhost:9001/BillingService/CreatePatientAccount
    public BillingServiceGrpcClient(
            @Value("${billing.service.address:localhost}") String serverAddress,
            @Value("${billing.service.grpc.port:9001}") int serverPort
            ) {
        log.info("Connecting to Billing service GRPC Server at {}:{}", serverAddress, serverPort);

        ManagedChannel managedChannel = ManagedChannelBuilder.forAddress(serverAddress, serverPort).usePlaintext().build();

        blockingStub = BillingServiceGrpc.newBlockingStub(managedChannel);
    }

    public List<BillingResponse> createBillingAccount(String patientId, String name, String email){
        BillingRequest billingRequest = BillingRequest.newBuilder()
                                            .setName(name)
                                            .setPatientId(patientId)
                                            .setEmail(email)
                                            .build();
        
        List<BillingResponse> responseList = new ArrayList<>();

        Iterator<BillingResponse> itr = blockingStub.createBillingAccount(billingRequest);

        while(itr.hasNext()){
            BillingResponse response = itr.next();
            responseList.add(response);
            log.info("Received response from gRPC server: \n{}\n", response);
        }
        
        return responseList;
    }
}

/*

@Value("${billing.service.address:localhost}")

@Value - Spring annotation that injects configuration values

"${billing.service.address:localhost}" - Reads from application.properties/yml with fallback

If property exists: Uses that value (e.g., billing-server-host)
If property doesn't exist: Uses default value localhost

*/
