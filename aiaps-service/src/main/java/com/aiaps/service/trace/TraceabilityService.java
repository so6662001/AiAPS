package com.aiaps.service.trace;

import com.aiaps.common.exception.BizException;
import com.aiaps.domain.inventory.InvItemBarcode;
import com.aiaps.domain.trace.TrcTraceLink;
import com.aiaps.mapper.inventory.InvItemBarcodeMapper;
import com.aiaps.mapper.inventory.InvStockBindMapper;
import com.aiaps.mapper.trace.TrcTraceLinkMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class TraceabilityService {

    private final TrcTraceLinkMapper traceLinkMapper;
    private final InvItemBarcodeMapper itemBarcodeMapper;
    private final InvStockBindMapper stockBindMapper;

    @Data
    public static class TraceNode {
        private String nodeType;
        private String cardNo;
        private String bindNo;
        private String resNo;
        private String itemBarcode;
        private String prdtName;
        private String specDisplay;
        private String patName;
        private String paName;
        private BigDecimal weight;
        private String contractNo;
        private String processType;
        private String scheduleNo;
        private List<TraceNode> children = new ArrayList<>();
        private List<TraceNode> parents = new ArrayList<>();
    }

    public TraceNode traceForward(String cardNo) {
        TraceNode root = buildNodeFromSource(cardNo);
        Set<String> visited = new HashSet<>();
        visited.add(cardNo);
        traceForwardRecursive(root, visited);
        return root;
    }

    public TraceNode traceBackward(String cardNo) {
        TraceNode root = buildNodeFromTarget(cardNo);
        Set<String> visited = new HashSet<>();
        visited.add(cardNo);
        traceBackwardRecursive(root, visited);
        return root;
    }

    public TraceNode traceByBarcode(String itemBarcode) {
        InvItemBarcode barcode = itemBarcodeMapper.selectByBarcode(itemBarcode);
        if (barcode == null) {
            throw new BizException("条码不存在: " + itemBarcode);
        }

        TraceNode node = new TraceNode();
        node.setNodeType("ITEM");
        node.setItemBarcode(barcode.getItemBarcode());
        node.setCardNo(barcode.getCardNo());
        node.setBindNo(barcode.getBindNo());
        node.setResNo(barcode.getResNo());
        node.setPrdtName(barcode.getPrdtName());
        node.setPatName(barcode.getPatName());
        node.setPaName(barcode.getPaName());
        node.setWeight(barcode.getItemWeight());
        node.setContractNo(barcode.getContractNo());

        if (barcode.getCardNo() != null) {
            TraceNode backward = traceBackward(barcode.getCardNo());
            node.setParents(backward.getParents());
        }

        return node;
    }

    public List<TraceNode> traceByContract(String contractNo) {
        List<TrcTraceLink> links = traceLinkMapper.selectByContract(contractNo);
        List<TraceNode> nodes = new ArrayList<>();

        Set<String> processedCards = new HashSet<>();
        for (TrcTraceLink link : links) {
            if (link.getSourceCardNo() != null && !processedCards.contains(link.getSourceCardNo())) {
                processedCards.add(link.getSourceCardNo());
                nodes.add(traceForward(link.getSourceCardNo()));
            }
        }

        return nodes;
    }

    @Transactional
    public void recordTraceLink(TrcTraceLink link) {
        traceLinkMapper.insert(link);
    }

    private void traceForwardRecursive(TraceNode node, Set<String> visited) {
        if (node.getCardNo() == null) {
            return;
        }

        List<TrcTraceLink> links = traceLinkMapper.selectBySourceCardNo(node.getCardNo());
        for (TrcTraceLink link : links) {
            String targetCard = link.getTargetCardNo();
            if (targetCard != null && !visited.contains(targetCard)) {
                visited.add(targetCard);

                TraceNode child = new TraceNode();
                child.setNodeType(link.getTargetType());
                child.setCardNo(link.getTargetCardNo());
                child.setBindNo(link.getTargetBindNo());
                child.setResNo(link.getTargetResNo());
                child.setPatName(link.getTargetPatName());
                child.setPaName(link.getTargetPaName());
                child.setWeight(link.getTargetWeight());
                child.setContractNo(link.getContractNo());
                child.setProcessType(link.getProcessType());
                child.setScheduleNo(link.getScheduleNo());

                traceForwardRecursive(child, visited);
                node.getChildren().add(child);
            }
        }
    }

    private void traceBackwardRecursive(TraceNode node, Set<String> visited) {
        if (node.getCardNo() == null) {
            return;
        }

        List<TrcTraceLink> links = traceLinkMapper.selectByTargetCardNo(node.getCardNo());
        for (TrcTraceLink link : links) {
            String sourceCard = link.getSourceCardNo();
            if (sourceCard != null && !visited.contains(sourceCard)) {
                visited.add(sourceCard);

                TraceNode parent = new TraceNode();
                parent.setNodeType(link.getSourceType());
                parent.setCardNo(link.getSourceCardNo());
                parent.setBindNo(link.getSourceBindNo());
                parent.setResNo(link.getSourceResNo());
                parent.setPatName(link.getSourcePatName());
                parent.setPaName(link.getSourcePaName());
                parent.setWeight(link.getSourceWeight());
                parent.setContractNo(link.getContractNo());
                parent.setProcessType(link.getProcessType());
                parent.setScheduleNo(link.getScheduleNo());

                traceBackwardRecursive(parent, visited);
                node.getParents().add(parent);
            }
        }
    }

    private TraceNode buildNodeFromSource(String cardNo) {
        TraceNode node = new TraceNode();
        node.setNodeType("SOURCE");
        node.setCardNo(cardNo);

        List<TrcTraceLink> links = traceLinkMapper.selectBySourceCardNo(cardNo);
        if (!links.isEmpty()) {
            TrcTraceLink first = links.get(0);
            node.setResNo(first.getSourceResNo());
            node.setBindNo(first.getSourceBindNo());
            node.setPatName(first.getSourcePatName());
            node.setPaName(first.getSourcePaName());
            node.setWeight(first.getSourceWeight());
            node.setContractNo(first.getContractNo());
        }

        return node;
    }

    private TraceNode buildNodeFromTarget(String cardNo) {
        TraceNode node = new TraceNode();
        node.setNodeType("TARGET");
        node.setCardNo(cardNo);

        List<TrcTraceLink> links = traceLinkMapper.selectByTargetCardNo(cardNo);
        if (!links.isEmpty()) {
            TrcTraceLink first = links.get(0);
            node.setResNo(first.getTargetResNo());
            node.setBindNo(first.getTargetBindNo());
            node.setPatName(first.getTargetPatName());
            node.setPaName(first.getTargetPaName());
            node.setWeight(first.getTargetWeight());
            node.setContractNo(first.getContractNo());
        }

        return node;
    }
}
