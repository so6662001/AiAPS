package com.aiaps.android.ui.issue;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;

import com.aiaps.android.R;
import com.aiaps.android.api.ApiClient;
import com.aiaps.android.api.ApiService;
import com.aiaps.android.databinding.ActivityMaterialIssueBinding;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MaterialIssueActivity extends AppCompatActivity {

    private ActivityMaterialIssueBinding binding;
    private ApiService apiService;

    private long scheduleId = -1;
    private String scheduleContract = "";
    private String scheduleGrade = "";
    private String scheduleSpec = "";

    private Map<String, Object> scannedStockInfo;

    private final ActivityResultLauncher<ScanOptions> scanLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() != null) {
                    String barcode = result.getContents();
                    binding.tvScanResult.setText(barcode);
                    lookupBarcode(barcode);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMaterialIssueBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        apiService = ApiClient.getInstance(this).getApiService();

        scheduleId = getIntent().getLongExtra("scheduleId", -1);
        if (scheduleId > 0) {
            loadScheduleInfo(scheduleId);
        }

        binding.btnScan.setOnClickListener(v -> {
            ScanOptions options = new ScanOptions();
            options.setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES);
            options.setPrompt("将条码置于取景框内扫描");
            options.setCameraId(0);
            options.setBeepEnabled(true);
            options.setBarcodeImageEnabled(false);
            options.setOrientationLocked(false);
            scanLauncher.launch(options);
        });

        binding.btnConfirmIssue.setOnClickListener(v -> confirmIssue());
        binding.btnChangeCoil.setOnClickListener(v -> resetScan());
    }

    private void loadScheduleInfo(long id) {
        apiService.getSchedule(id).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Map<String, Object> data = response.body();
                    binding.tvScheduleNo.setText(getStr(data, "scheduleNo"));
                    binding.tvProduct.setText(getStr(data, "product"));
                    binding.tvGrade.setText(getStr(data, "grade"));
                    binding.tvOrigin.setText(getStr(data, "origin"));
                    binding.tvContractNo.setText(getStr(data, "contractNo"));
                    scheduleContract = getStr(data, "contractNo");
                    scheduleGrade = getStr(data, "grade");
                    scheduleSpec = getStr(data, "spec");
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                Toast.makeText(MaterialIssueActivity.this,
                        R.string.error_network, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void lookupBarcode(String barcode) {
        apiService.traceByBarcode(barcode).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    scannedStockInfo = response.body();
                    showScannedInfo(scannedStockInfo);
                    validateMaterial(scannedStockInfo);
                } else {
                    Toast.makeText(MaterialIssueActivity.this,
                            "未找到物料信息", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                Toast.makeText(MaterialIssueActivity.this,
                        R.string.error_network, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showScannedInfo(Map<String, Object> info) {
        binding.cardScannedInfo.setVisibility(View.VISIBLE);
        binding.tvResNo.setText(getStr(info, "resNo"));
        binding.tvCardNo.setText(getStr(info, "cardNo"));
        binding.tvMaterial.setText(getStr(info, "material"));
        binding.tvScannedGrade.setText(getStr(info, "grade"));
        binding.tvScannedOrigin.setText(getStr(info, "origin"));
        binding.tvWeight.setText(getStr(info, "weight"));
        binding.tvScannedContract.setText(getStr(info, "contractNo"));
    }

    private void validateMaterial(Map<String, Object> info) {
        binding.cardValidation.setVisibility(View.VISIBLE);

        boolean contractOk = scheduleContract.equals(getStr(info, "contractNo"));
        boolean gradeOk = scheduleGrade.equals(getStr(info, "grade"));
        boolean specOk = scheduleSpec.equals(getStr(info, "spec"));

        setValidationIcon(binding.ivContractMatch, contractOk);
        setValidationIcon(binding.ivGradeMatch, gradeOk);
        setValidationIcon(binding.ivSpecMatch, specOk);

        binding.tvContractMatch.setText(getString(R.string.contract_match) + " - "
                + (contractOk ? getString(R.string.matched) : getString(R.string.not_matched)));
        binding.tvGradeMatch.setText(getString(R.string.grade_match) + " - "
                + (gradeOk ? getString(R.string.matched) : getString(R.string.not_matched)));
        binding.tvSpecMatch.setText(getString(R.string.spec_match) + " - "
                + (specOk ? getString(R.string.matched) : getString(R.string.not_matched)));
    }

    private void setValidationIcon(android.widget.ImageView iv, boolean pass) {
        if (pass) {
            iv.setImageResource(android.R.drawable.presence_online);
        } else {
            iv.setImageResource(android.R.drawable.presence_busy);
        }
    }

    private void confirmIssue() {
        if (scannedStockInfo == null) {
            Toast.makeText(this, "请先扫描物料", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> issueData = new HashMap<>();
        issueData.put("scheduleId", scheduleId);
        issueData.put("stockId", scannedStockInfo.get("stockId"));
        issueData.put("resNo", scannedStockInfo.get("resNo"));
        issueData.put("weight", scannedStockInfo.get("weight"));

        apiService.createIssue(issueData).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Object issueId = response.body().get("id");
                    if (issueId != null) {
                        long id = ((Number) issueId).longValue();
                        executeIssue(id);
                    }
                } else {
                    Toast.makeText(MaterialIssueActivity.this,
                            "上料创建失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                Toast.makeText(MaterialIssueActivity.this,
                        R.string.error_network, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void executeIssue(long issueId) {
        apiService.executeIssue(issueId).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(MaterialIssueActivity.this,
                            R.string.success, Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    Toast.makeText(MaterialIssueActivity.this,
                            "上料执行失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                Toast.makeText(MaterialIssueActivity.this,
                        R.string.error_network, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void resetScan() {
        scannedStockInfo = null;
        binding.tvScanResult.setText(R.string.scan_hint);
        binding.cardScannedInfo.setVisibility(View.GONE);
        binding.cardValidation.setVisibility(View.GONE);
    }

    private String getStr(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : "--";
    }
}
