package com.aiaps.android.ui;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.aiaps.android.databinding.ActivityMainBinding;
import com.aiaps.android.ui.issue.MaterialIssueActivity;
import com.aiaps.android.ui.receipt.WarehouseReceiptActivity;
import com.aiaps.android.ui.report.ProductionReportActivity;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        binding.cardMaterialIssue.setOnClickListener(v ->
                startActivity(new Intent(this, MaterialIssueActivity.class)));

        binding.cardProductionReport.setOnClickListener(v ->
                startActivity(new Intent(this, ProductionReportActivity.class)));

        binding.cardWarehouseReceipt.setOnClickListener(v ->
                startActivity(new Intent(this, WarehouseReceiptActivity.class)));
    }
}
