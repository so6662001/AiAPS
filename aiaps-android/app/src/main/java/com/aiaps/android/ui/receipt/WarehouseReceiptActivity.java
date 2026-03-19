package com.aiaps.android.ui.receipt;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;

import com.aiaps.android.R;
import com.aiaps.android.api.ApiClient;
import com.aiaps.android.api.ApiService;
import com.aiaps.android.databinding.ActivityWarehouseReceiptBinding;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class WarehouseReceiptActivity extends AppCompatActivity {

    private ActivityWarehouseReceiptBinding binding;
    private ApiService apiService;

    private Map<String, Object> receiptInfo;

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
        binding = ActivityWarehouseReceiptBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        apiService = ApiClient.getInstance(this).getApiService();

        setupWarehouseSpinner();
        setupBundleCalc();

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

        binding.btnConfirmReceipt.setOnClickListener(v -> confirmReceipt());
        binding.btnPrintLabel.setOnClickListener(v ->
                Toast.makeText(this, R.string.print_not_available, Toast.LENGTH_SHORT).show());
    }

    private void setupWarehouseSpinner() {
        String[] warehouses = {"成品库", "半成品库", "待检库"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, warehouses);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerWarehouse.setAdapter(adapter);
    }

    private void setupBundleCalc() {
        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                calculateBundles();
            }
        };
        binding.etReceiptQty.addTextChangedListener(watcher);
        binding.etQtyPerBundle.addTextChangedListener(watcher);
    }

    private void calculateBundles() {
        int totalQty = parseInt(binding.etReceiptQty.getText().toString());
        int perBundle = parseInt(binding.etQtyPerBundle.getText().toString());

        if (perBundle > 0) {
            int bundles = (int) Math.ceil((double) totalQty / perBundle);
            binding.tvTotalBundles.setText(String.valueOf(bundles));
        } else {
            binding.tvTotalBundles.setText("0");
        }
    }

    private void lookupBarcode(String barcode) {
        apiService.traceByBarcode(barcode).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    receiptInfo = response.body();
                    showReceiptInfo(receiptInfo);
                } else {
                    Toast.makeText(WarehouseReceiptActivity.this,
                            "未找到物料信息", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                Toast.makeText(WarehouseReceiptActivity.this,
                        R.string.error_network, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showReceiptInfo(Map<String, Object> info) {
        binding.cardReceiptInfo.setVisibility(View.VISIBLE);
        binding.tvSourceSchedule.setText(getStr(info, "scheduleNo"));
        binding.tvMaterial.setText(getStr(info, "material"));
        binding.tvGrade.setText(getStr(info, "grade"));
        binding.tvOrigin.setText(getStr(info, "origin"));
        binding.tvContractNo.setText(getStr(info, "contractNo"));
    }

    private void confirmReceipt() {
        Map<String, Object> receipt = new HashMap<>();
        if (receiptInfo != null) {
            receipt.put("scheduleId", receiptInfo.get("scheduleId"));
            receipt.put("material", receiptInfo.get("material"));
        }
        receipt.put("qty", parseInt(binding.etReceiptQty.getText().toString()));
        receipt.put("weight", parseDouble(binding.etReceiptWeight.getText().toString()));
        receipt.put("actualWeight", parseDouble(binding.etActualWeight.getText().toString()));
        receipt.put("length", parseDouble(binding.etLength.getText().toString()));

        String qcStatus = getQcStatus();
        receipt.put("qcStatus", qcStatus);
        receipt.put("warehouse", binding.spinnerWarehouse.getSelectedItem().toString());
        receipt.put("qtyPerBundle", parseInt(binding.etQtyPerBundle.getText().toString()));
        receipt.put("cardRemark", binding.etRemark1.getText().toString().trim());
        receipt.put("cardRemark2", binding.etRemark2.getText().toString().trim());

        apiService.createReceipt(receipt).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(WarehouseReceiptActivity.this,
                            R.string.success, Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    Toast.makeText(WarehouseReceiptActivity.this,
                            "入库确认失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                Toast.makeText(WarehouseReceiptActivity.this,
                        R.string.error_network, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String getQcStatus() {
        int checkedId = binding.rgQcStatus.getCheckedRadioButtonId();
        if (checkedId == R.id.rbQcPass) {
            return getString(R.string.qc_pass);
        } else if (checkedId == R.id.rbQcFail) {
            return getString(R.string.qc_fail);
        } else if (checkedId == R.id.rbQcExempt) {
            return getString(R.string.qc_exempt);
        }
        return getString(R.string.qc_pass);
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
