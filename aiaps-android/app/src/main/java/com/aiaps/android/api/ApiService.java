package com.aiaps.android.api;

import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ApiService {

    @GET("v1/schedule/{id}")
    Call<Map<String, Object>> getSchedule(@Path("id") long id);

    @GET("v1/schedule")
    Call<Map<String, Object>> getScheduleQueue(
            @Query("wcId") long wcId,
            @Query("status") String status
    );

    @POST("v1/report")
    Call<Map<String, Object>> submitReport(@Body Map<String, Object> report);

    @POST("v1/material-issue")
    Call<Map<String, Object>> createIssue(@Body Map<String, Object> issue);

    @PUT("v1/material-issue/{id}/execute")
    Call<Map<String, Object>> executeIssue(@Path("id") long id);

    @GET("v1/stock/{stockId}")
    Call<Map<String, Object>> getStockInfo(@Path("stockId") long stockId);

    @GET("v1/trace/barcode/{barcode}")
    Call<Map<String, Object>> traceByBarcode(@Path("barcode") String barcode);

    @POST("v1/inventory/receipt")
    Call<Map<String, Object>> createReceipt(@Body Map<String, Object> receipt);
}
