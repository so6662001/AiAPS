package com.aiaps.service.system;

import com.aiaps.common.exception.BizException;
import com.aiaps.domain.system.SysAgreementSign;
import com.aiaps.domain.system.SysAgreementVersion;
import com.aiaps.domain.system.SysUserAcknowledgment;
import com.aiaps.mapper.system.SysAgreementSignMapper;
import com.aiaps.mapper.system.SysAgreementVersionMapper;
import com.aiaps.mapper.system.SysUserAcknowledgmentMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgreementService {

    private final SysAgreementVersionMapper versionMapper;
    private final SysAgreementSignMapper signMapper;
    private final SysUserAcknowledgmentMapper acknowledgmentMapper;

    @Transactional
    public void createVersion(SysAgreementVersion version) {
        version.setCreatedTime(new Date());

        if (Boolean.TRUE.equals(version.getIsCurrent())) {
            versionMapper.update(null, new LambdaUpdateWrapper<SysAgreementVersion>()
                    .eq(SysAgreementVersion::getAgreementType, version.getAgreementType())
                    .set(SysAgreementVersion::getIsCurrent, false));
        }

        versionMapper.insert(version);
        log.info("Created agreement version: type={}, versionNo={}, versionId={}",
                version.getAgreementType(), version.getVersionNo(), version.getVersionId());
    }

    public SysAgreementVersion getCurrentVersion(String agreementType) {
        return versionMapper.selectCurrentByType(agreementType);
    }

    public List<SysAgreementVersion> getVersionHistory(String agreementType) {
        return versionMapper.selectList(new LambdaQueryWrapper<SysAgreementVersion>()
                .eq(SysAgreementVersion::getAgreementType, agreementType)
                .orderByDesc(SysAgreementVersion::getEffectiveDate));
    }

    public Map<String, Object> checkNeedSign(Long enterpriseId, String agreementType) {
        Map<String, Object> result = new HashMap<>();

        SysAgreementVersion currentVersion = versionMapper.selectCurrentByType(agreementType);
        if (currentVersion == null) {
            result.put("needSign", false);
            return result;
        }

        result.put("currentVersionId", currentVersion.getVersionId());
        result.put("currentVersionNo", currentVersion.getVersionNo());
        result.put("changeSummary", currentVersion.getChangeSummary());

        SysAgreementSign activeSign = signMapper.selectActiveByEnterprise(enterpriseId, agreementType);
        if (activeSign == null) {
            result.put("needSign", true);
            result.put("signedVersionNo", null);
        } else if (!activeSign.getVersionId().equals(currentVersion.getVersionId())) {
            result.put("needSign", true);
            result.put("signedVersionNo", activeSign.getVersionNo());
        } else {
            result.put("needSign", false);
            result.put("signedVersionNo", activeSign.getVersionNo());
        }

        return result;
    }

    @Transactional
    public void signAgreement(SysAgreementSign sign) {
        SysAgreementVersion version = versionMapper.selectById(sign.getVersionId());
        if (version == null) {
            throw new BizException("协议版本不存在: " + sign.getVersionId());
        }
        if (!Boolean.TRUE.equals(version.getIsCurrent())) {
            throw new BizException("协议版本已不是当前版本，请刷新后重试");
        }

        SysAgreementSign existingSign = signMapper.selectActiveByEnterprise(
                sign.getEnterpriseId(), sign.getAgreementType());
        if (existingSign != null) {
            existingSign.setSignStatus("SUPERSEDED");
            existingSign.setSupersededBy(sign.getSignId());
            signMapper.updateById(existingSign);
        }

        sign.setSignStatus("ACTIVE");
        sign.setSignTime(new Date());
        signMapper.insert(sign);

        if (existingSign != null) {
            existingSign.setSupersededBy(sign.getSignId());
            signMapper.updateById(existingSign);
        }

        log.info("Enterprise {} signed agreement: type={}, versionNo={}",
                sign.getEnterpriseId(), sign.getAgreementType(), sign.getVersionNo());
    }

    public Map<String, Object> checkNeedAcknowledge(String userId, Long enterpriseId) {
        Map<String, Object> result = new HashMap<>();

        SysAgreementVersion currentVersion = versionMapper.selectCurrentByType("PRIVACY_NOTICE");
        if (currentVersion == null) {
            result.put("needAck", false);
            result.put("adminSigned", true);
            return result;
        }

        SysUserAcknowledgment ack = acknowledgmentMapper.selectByUserAndVersion(
                userId, currentVersion.getVersionId());
        result.put("needAck", ack == null);
        result.put("currentVersionId", currentVersion.getVersionId());
        result.put("changeSummary", currentVersion.getChangeSummary());

        SysAgreementSign adminSign = signMapper.selectActiveByEnterprise(enterpriseId, "DATA_PROCESS");
        SysAgreementVersion currentDataProcess = versionMapper.selectCurrentByType("DATA_PROCESS");
        boolean adminSigned = adminSign != null && currentDataProcess != null
                && adminSign.getVersionId().equals(currentDataProcess.getVersionId());
        result.put("adminSigned", adminSigned);

        return result;
    }

    @Transactional
    public void acknowledge(String userId, String userName, Long enterpriseId, Long versionId, String ip) {
        SysUserAcknowledgment existing = acknowledgmentMapper.selectByUserAndVersion(userId, versionId);
        if (existing != null) {
            return;
        }

        SysUserAcknowledgment ack = new SysUserAcknowledgment();
        ack.setUserId(userId);
        ack.setUserName(userName);
        ack.setEnterpriseId(enterpriseId);
        ack.setVersionId(versionId);
        ack.setAckTime(new Date());
        ack.setAckIp(ip);
        acknowledgmentMapper.insert(ack);

        log.info("User {} acknowledged privacy notice version {}", userId, versionId);
    }

    public Map<String, Boolean> getEnterpriseAuthScope(Long enterpriseId) {
        SysAgreementSign activeSign = signMapper.selectActiveByEnterprise(enterpriseId, "DATA_PROCESS");
        Map<String, Boolean> scope = new LinkedHashMap<>();

        if (activeSign == null) {
            scope.put("authAlgoTraining", false);
            scope.put("authRegionIndex", false);
            scope.put("authBenchmark", false);
            scope.put("authSupplyMatch", false);
            scope.put("authPriceAnalysis", false);
            scope.put("authProductImprove", false);
            scope.put("authCustomerSuccess", false);
            return scope;
        }

        scope.put("authAlgoTraining", Boolean.TRUE.equals(activeSign.getAuthAlgoTraining()));
        scope.put("authRegionIndex", Boolean.TRUE.equals(activeSign.getAuthRegionIndex()));
        scope.put("authBenchmark", Boolean.TRUE.equals(activeSign.getAuthBenchmark()));
        scope.put("authSupplyMatch", Boolean.TRUE.equals(activeSign.getAuthSupplyMatch()));
        scope.put("authPriceAnalysis", Boolean.TRUE.equals(activeSign.getAuthPriceAnalysis()));
        scope.put("authProductImprove", Boolean.TRUE.equals(activeSign.getAuthProductImprove()));
        scope.put("authCustomerSuccess", Boolean.TRUE.equals(activeSign.getAuthCustomerSuccess()));
        return scope;
    }

    public List<SysAgreementSign> getSignHistory(Long enterpriseId) {
        return signMapper.selectList(new LambdaQueryWrapper<SysAgreementSign>()
                .eq(SysAgreementSign::getEnterpriseId, enterpriseId)
                .orderByDesc(SysAgreementSign::getSignTime));
    }

    @Transactional
    public void updateAuthScope(Long enterpriseId, Map<String, Boolean> newScope,
                                String signerUserId, String signerName) {
        SysAgreementSign previousSign = signMapper.selectActiveByEnterprise(enterpriseId, "DATA_PROCESS");
        if (previousSign == null) {
            throw new BizException("企业尚未签署数据处理协议，无法修改授权范围");
        }

        SysAgreementSign newSign = new SysAgreementSign();
        newSign.setEnterpriseId(previousSign.getEnterpriseId());
        newSign.setEnterpriseName(previousSign.getEnterpriseName());
        newSign.setSignerUserId(signerUserId);
        newSign.setSignerName(signerName);
        newSign.setSignerRole(previousSign.getSignerRole());
        newSign.setVersionId(previousSign.getVersionId());
        newSign.setAgreementType(previousSign.getAgreementType());
        newSign.setVersionNo(previousSign.getVersionNo());

        newSign.setAuthAlgoTraining(newScope.getOrDefault("authAlgoTraining", previousSign.getAuthAlgoTraining()));
        newSign.setAuthRegionIndex(newScope.getOrDefault("authRegionIndex", previousSign.getAuthRegionIndex()));
        newSign.setAuthBenchmark(newScope.getOrDefault("authBenchmark", previousSign.getAuthBenchmark()));
        newSign.setAuthSupplyMatch(newScope.getOrDefault("authSupplyMatch", previousSign.getAuthSupplyMatch()));
        newSign.setAuthPriceAnalysis(newScope.getOrDefault("authPriceAnalysis", previousSign.getAuthPriceAnalysis()));
        newSign.setAuthProductImprove(newScope.getOrDefault("authProductImprove", previousSign.getAuthProductImprove()));
        newSign.setAuthCustomerSuccess(newScope.getOrDefault("authCustomerSuccess", previousSign.getAuthCustomerSuccess()));

        newSign.setSignStatus("ACTIVE");
        newSign.setSignTime(new Date());
        newSign.setSignIp(previousSign.getSignIp());
        newSign.setSignDevice(previousSign.getSignDevice());

        previousSign.setSignStatus("SUPERSEDED");
        signMapper.updateById(previousSign);

        signMapper.insert(newSign);

        previousSign.setSupersededBy(newSign.getSignId());
        signMapper.updateById(previousSign);

        log.info("Enterprise {} updated auth scope, new signId={}", enterpriseId, newSign.getSignId());
    }
}
