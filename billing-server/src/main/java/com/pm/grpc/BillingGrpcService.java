package com.pm.grpc;

import billing.BillingRequest;
import billing.BillingResponse;
import billing.BillingServiceGrpc;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@GrpcService
public class BillingGrpcService extends BillingServiceGrpc.BillingServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(BillingGrpcService.class);
    @Override
    public void createBillingAccount(BillingRequest request, StreamObserver<BillingResponse> responseObserver) {
        log.info("createBillingAccount request receieved {}", request.toString());

        // Business Logic - saving to db etc.

        BillingResponse response = BillingResponse.newBuilder()
                .setAccountId("1234")
                .setStatus("ACTIVE")
                .build();

        BillingResponse response2 = BillingResponse.newBuilder()
                .setAccountId("5678")
                .setStatus("INACTIVE")
                .build();

        // Send the response back to client (PatientService)
        responseObserver.onNext(response);

        responseObserver.onNext(response2 );

        // To tell that we are done sending responses (many responses can be sent simultaneously)
        responseObserver.onCompleted();
    }
}
