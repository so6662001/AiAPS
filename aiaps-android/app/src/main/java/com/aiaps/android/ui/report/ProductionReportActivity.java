package com.aiaps.android.ui.report;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.aiaps.android.R;
import com.aiaps.android.api.ApiClient;
import com.aiaps.android.api.ApiService;
import com.aiaps.android.databinding.ActivityProductionReportBinding;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProductionReportActivity extends AppCompatActivity {

    private ActivityProductionReportBinding binding;
    private ApiService apiService;

    private long scheduleId = -1;
    private double inputWeight = 0;
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProductionReportBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        apiService = ApiClient.getInstance(this).getApiService();

        setupShiftSpinner();
        setupTimeSelectors();
        setupWeightWatchers();

        scheduleId = getIntent().getLongExtra("scheduleId", -1);
        if (scheduleId > 0) {
            loadScheduleInfo(scheduleId);
        }

        binding.btnSubmitReport.setOnClickListener(v -> submitReport());
    }

    private void setupShiftSpinner() {
        String[] shifts = {
                getString(R.string.shift_day),
                getString(R.string.shift_mid),
                getString(R.string.shift_night)
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, shifts);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerShift.setAdapter(adapter);
    }

    private void setupTimeSelectors() {
        binding.tvStartTime.setOnClickListener(v -> showDateTimePicker(true));
        binding.tvEndTime.setOnClickListener(v -> showDateTimePicker(false));
    }

    private void showDateTimePicker(boolean isStart) {
        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            cal.set(Calendar.YEAR, year);
            cal.set(Calendar.MONTH, month);
            cal.set(Calendar.DAY_OF_MONTH, dayOfMonth);

            new TimePickerDialog(this, (tv, hourOfDay, minute) -> {
                cal.set(Calendar.HOUR_OF_DAY, hourOfDay);
                cal.set(Calendar.MINUTE, minute);
                String formatted = sdf.format(cal.getTime());
                if (isStart) {
                    binding.tvStartTime.setText(formatted);
                } else {
                    binding.tvEndTime.setText(formatted);
                }
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void setupWeightWatchers() {
        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                calculateYield();
            }
        };
        binding.etGoodWeight.addTextChangedListener(watcher);
        binding.etScrapWeight.addTextChangedListener(watcher);
    }

    private void calculateYield() {
        double goodWeight = parseDouble(binding.etGoodWeight.getText().toString());
        double scrapWeight = parseDouble(binding.etScrapWeight.getText().toString());
        double outputWeight = goodWeight + scrapWeight;

        binding.tvInputWeight.setText(String.format(Locale.US, "%.3f", inputWeight));
        binding.tvOutputWeight.setText(String.format(Locale.US, "%.3f", goodWeight));

        if (inputWeight > 0) {
            double yieldRate = (goodWeight / inputWeight) * 100;
            binding.tvYieldRate.setText(String.format(Locale.US, "%.2f%%", yieldRate));
        } else {
            binding.tvYieldRate.setText("0.00%");
        }
    }

    private void loadScheduleInfo(long id) {
        apiService.getSchedule(id).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Map<String, Object> data = response.body();
                    binding.tvScheduleNo.setText(getStr(data, "scheduleNo"));
                    binding.tvProductSpec.setText(getStr(data, "productSpec"));
                    binding.tvGrade.setText(getStr(data, "grade"));
                    binding.tvOrigin.setText(getStr(data, "origin"));
                    binding.tvContractNo.setText(getStr(data, "contractNo"));
                    binding.tvLength.setText(getStr(data, "length"));
                    binding.tvMold.setText(getStr(data, "mold"));
                    binding.tvCoilNo.setText(getStr(data, "coilNo"));
                    binding.tvUsageWeight.setText(getStr(data, "usageWeight"));
                    inputWeight = parseDouble(getStr(data, "usageWeight"));
                    calculateYield();
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                Toast.makeText(ProductionReportActivity.this,
                        R.string.error_network, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void submitReport() {
        Map<String, Object> report = new HashMap<>();
        report.put("scheduleId", scheduleId);
        report.put("goodQty", parseInt(binding.etGoodQty.getText().toString()));
        report.put("goodWeight", parseDouble(binding.etGoodWeight.getText().toString()));
        report.put("scrapQty", parseInt(binding.etScrapQty.getText().toString()));
        report.put("scrapWeight", parseDouble(binding.etScrapWeight.getText().toString()));
        report.put("outputLength", parseDouble(binding.etOutputLength.getText().toString()));
        report.put("shift", binding.spinnerShift.getSelectedItem().toString());
        report.put("operator", binding.etOperator.getText().toString().trim());
        report.put("startTime", binding.tvStartTime.getText().toString());
        report.put("endTime", binding.tvEndTime.getText().toString());

        apiService.submitReport(report).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(ProductionReportActivity.this,
                            R.string.success, Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    Toast.makeText(ProductionReportActivity.this,
                            "报工提交失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                Toast.makeText(ProductionReportActivity.this,
                        R.string.error_network, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private double parseDouble(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String getStr(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : "--";
    }
}
