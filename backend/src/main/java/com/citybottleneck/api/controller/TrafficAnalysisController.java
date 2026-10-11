package com.citybottleneck.api.controller;

import com.citybottleneck.api.model.TrafficModels.AnalysisRequest;
import com.citybottleneck.api.model.TrafficModels.AnalysisResponse;
import com.citybottleneck.api.model.TrafficModels.ModelInfo;
import com.citybottleneck.api.model.TrafficModels.Place;
import com.citybottleneck.api.model.TrafficModels.ScenarioRequest;
import com.citybottleneck.api.model.TrafficModels.ScenarioResponse;
import com.citybottleneck.api.service.TrafficAnalysisService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api")
public class TrafficAnalysisController {

    private final TrafficAnalysisService trafficAnalysisService;

    public TrafficAnalysisController(TrafficAnalysisService trafficAnalysisService) {
        this.trafficAnalysisService = trafficAnalysisService;
    }

    @GetMapping("/geocode")
    public List<Place> geocode(@RequestParam("q") @NotBlank @Size(max = 160) String query) {
        return trafficAnalysisService.geocode(query);
    }

    @PostMapping("/analysis")
    public AnalysisResponse analyze(@Valid @RequestBody AnalysisRequest request) {
        return trafficAnalysisService.analyze(request);
    }

    @PostMapping("/analysis/scenario")
    public ScenarioResponse scenario(@Valid @RequestBody ScenarioRequest request) {
        return trafficAnalysisService.compareScenario(request);
    }

    @GetMapping("/traffic-model")
    public ModelInfo modelInfo() {
        return trafficAnalysisService.modelInfo();
    }
}