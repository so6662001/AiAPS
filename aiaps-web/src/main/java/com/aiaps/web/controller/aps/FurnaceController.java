package com.aiaps.web.controller.aps;

import com.aiaps.common.result.R;
import com.aiaps.domain.aps.ApsFurnaceCharge;
import com.aiaps.domain.aps.ApsFurnaceChargeLayer;
import com.aiaps.domain.base.BasAnnealRecipe;
import com.aiaps.domain.base.BasFurnace;
import com.aiaps.service.aps.FurnaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/v1/furnace")
@RequiredArgsConstructor
public class FurnaceController {

    private final FurnaceService furnaceService;

    @GetMapping
    public R<List<BasFurnace>> listFurnaces() {
        return R.ok(furnaceService.listFurnaces());
    }

    @GetMapping("/{id}")
    public R<BasFurnace> getFurnaceById(@PathVariable Long id) {
        return R.ok(furnaceService.getFurnaceById(id));
    }

    @PutMapping("/{id}/status")
    public R<Void> updateFurnaceStatus(@PathVariable Long id, @RequestParam String status) {
        furnaceService.updateFurnaceStatus(id, status);
        return R.ok();
    }

    @GetMapping("/recipe/compatible")
    public R<List<BasAnnealRecipe>> getCompatibleRecipes(@RequestParam String gradeCode) {
        return R.ok(furnaceService.getCompatibleRecipes(gradeCode));
    }

    @PostMapping("/charge")
    public R<Void> createCharge(@Valid @RequestBody ApsFurnaceCharge charge) {
        furnaceService.createCharge(charge);
        return R.ok();
    }

    @PostMapping("/charge/{chargeId}/load-layer")
    public R<Void> loadLayer(
            @PathVariable Long chargeId,
            @RequestParam int layerNo,
            @RequestParam Long stockId) {
        furnaceService.loadLayer(chargeId, layerNo, stockId);
        return R.ok();
    }

    @PostMapping("/charge/{chargeId}/start-anneal")
    public R<Void> startAnneal(@PathVariable Long chargeId) {
        furnaceService.startAnneal(chargeId);
        return R.ok();
    }

    @PostMapping("/charge/{chargeId}/complete")
    public R<Void> completeCharge(@PathVariable Long chargeId) {
        furnaceService.completeCharge(chargeId);
        return R.ok();
    }

    @GetMapping("/charge/{chargeId}")
    public R<Map<String, Object>> getChargeDetail(@PathVariable Long chargeId) {
        ApsFurnaceCharge charge = furnaceService.getChargeDetail(chargeId);
        List<ApsFurnaceChargeLayer> layers = furnaceService.getChargeLayers(chargeId);
        Map<String, Object> result = new HashMap<>();
        result.put("charge", charge);
        result.put("layers", layers);
        return R.ok(result);
    }

    @GetMapping("/charge/gantt")
    public R<List<ApsFurnaceCharge>> getGanttData(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date dateFrom,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date dateTo) {
        return R.ok(furnaceService.getGanttData(dateFrom, dateTo));
    }
}
