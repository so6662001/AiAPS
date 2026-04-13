package com.aiaps.web.controller.system;

import com.aiaps.common.result.R;
import com.aiaps.domain.system.SysAgreementSign;
import com.aiaps.domain.system.SysAgreementVersion;
import com.aiaps.service.system.AgreementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/v1/agreement")
@RequiredArgsConstructor
public class AgreementController {

    private final AgreementService agreementService;

    @PostMapping("/version")
    public R<Void> createVersion(@RequestBody SysAgreementVersion version) {
        agreementService.createVersion(version);
        return R.ok();
    }

    @GetMapping("/version/current")
    public R<Map<String, SysAgreementVersion>> getCurrentVersions() {
        Map<String, SysAgreementVersion> result = new LinkedHashMap<>();
        for (String type : Arrays.asList("SERVICE", "DATA_PROCESS", "PRIVACY_NOTICE", "VALUE_ADDED")) {
            SysAgreementVersion v = agreementService.getCurrentVersion(type);
            if (v != null) {
                result.put(type, v);
            }
        }
        return R.ok(result);
    }

    @GetMapping("/version/{type}/history")
    public R<List<SysAgreementVersion>> getVersionHistory(@PathVariable String type) {
        return R.ok(agreementService.getVersionHistory(type));
    }

    @PostMapping("/sign")
    public R<Void> signAgreement(@RequestBody SysAgreementSign sign) {
        agreementService.signAgreement(sign);
        return R.ok();
    }

    @GetMapping("/sign/check")
    public R<Map<String, Object>> checkNeedSign(@RequestParam Long enterpriseId,
                                                @RequestParam String agreementType) {
        return R.ok(agreementService.checkNeedSign(enterpriseId, agreementType));
    }

    @GetMapping("/sign/history")
    public R<List<SysAgreementSign>> getSignHistory(@RequestParam Long enterpriseId) {
        return R.ok(agreementService.getSignHistory(enterpriseId));
    }

    @GetMapping("/sign/{signId}/download")
    public R<Map<String, String>> downloadSignArchive(@PathVariable Long signId) {
        Map<String, String> result = new HashMap<>();
        result.put("signId", signId.toString());
        result.put("message", "OSS download URL would be generated here");
        return R.ok(result);
    }

    @PutMapping("/auth-scope")
    public R<Void> updateAuthScope(@RequestBody Map<String, Object> body) {
        Long enterpriseId = Long.valueOf(body.get("enterpriseId").toString());
        String signerUserId = (String) body.get("signerUserId");
        String signerName = (String) body.get("signerName");

        @SuppressWarnings("unchecked")
        Map<String, Boolean> scope = (Map<String, Boolean>) body.get("scope");

        agreementService.updateAuthScope(enterpriseId, scope, signerUserId, signerName);
        return R.ok();
    }

    @PostMapping("/acknowledge")
    public R<Void> acknowledge(@RequestBody Map<String, Object> body) {
        String userId = (String) body.get("userId");
        String userName = (String) body.get("userName");
        Long enterpriseId = Long.valueOf(body.get("enterpriseId").toString());
        Long versionId = Long.valueOf(body.get("versionId").toString());
        String ip = (String) body.get("ip");
        agreementService.acknowledge(userId, userName, enterpriseId, versionId, ip);
        return R.ok();
    }

    @GetMapping("/acknowledge/check")
    public R<Map<String, Object>> checkNeedAcknowledge(@RequestParam String userId,
                                                       @RequestParam Long enterpriseId) {
        return R.ok(agreementService.checkNeedAcknowledge(userId, enterpriseId));
    }

    @GetMapping("/enterprise/{id}/scope")
    public R<Map<String, Boolean>> getEnterpriseAuthScope(@PathVariable("id") Long enterpriseId) {
        return R.ok(agreementService.getEnterpriseAuthScope(enterpriseId));
    }
}
