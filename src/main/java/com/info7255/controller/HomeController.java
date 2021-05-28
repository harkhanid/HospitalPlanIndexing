package com.info7255.controller;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.*;


import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.info7255.dao.PlanDao;
import com.info7255.exception.NoKeyException;
import com.info7255.service.MessageQueue;
import com.info7255.service.PlanService;
import com.info7255.service.TokenService;
import com.info7255.validator.SchemaValidator;




@RestController
public class HomeController {

	@Autowired
    private SchemaValidator planValidator;

    @Autowired
    private PlanService planService;

    @Autowired
    private TokenService tokenService;
    
    @Autowired
    private MessageQueue messageQueueService;
	
    @Autowired
    private PlanDao planDao;
	
	Map<String, Object> map = new HashMap<String, Object>();
	
	@RequestMapping
	public String home() {
		
		return "Welcome to a Spring Boot Application!";
	}
	
	
	private String initialKey = "";

    @GetMapping(value = "/token", produces = "application/json")
    public ResponseEntity<String> getToken() throws NoSuchPaddingException, InvalidKeyException, UnsupportedEncodingException, IllegalBlockSizeException, BadPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException {
        String token = tokenService.generateToken();
        return new ResponseEntity<String>("{\"token\": "+ token + "}", HttpStatus.CREATED);
    }

    @PostMapping(value = "/plan", produces = "application/json")
    public ResponseEntity<Object> createPlan(@RequestBody(required = false) String plan, @RequestHeader HttpHeaders headers) throws Exception {
    	
        if (plan == null || plan.isEmpty()){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new JSONObject().put("message", "Plan is empty").toString());
        }

        String token = tokenService.authorizeToken(headers);
        if (!token.equals("Valid Token")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new JSONObject().put("Authentication Error", token).toString());
        }

        JSONObject json = new JSONObject(plan);
        System.out.println(json);
        try{
            planValidator.validatePlan(json);
        }catch(ValidationException ex){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new JSONObject().put("error", ex.getErrorMessage()).toString());
        }

        String key = json.get("objectType").toString() + "_" + json.get("objectId").toString();
        initialKey = key;

        if(planService.isKeyPresent(key)){
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new JSONObject().put("message", "Plan already exist").toString());
        }

        planService.addPlanETag(json, json.get("objectId").toString());
        Map<String, Object> outputMap = new HashMap<>();
        outputMap = planService.getDeletePlan(key, outputMap, false);
        
        String eTag = planService.createETag(key, new JSONObject(outputMap).toString());
        //String newEtag =  planService.savaPlan(json.get("objectId").toString(), json);
        return ResponseEntity.status(HttpStatus.CREATED).eTag(eTag).body(new JSONObject().put("message", "Plan is created").toString());
    }

    @GetMapping(value = "/{type}/{objectId}", produces = "application/json")
    public ResponseEntity<Object> getPlan(@PathVariable String type, @PathVariable String objectId, @RequestHeader HttpHeaders headers) {

        String existingEtag = "";
        String token = tokenService.authorizeToken(headers);
        if (!token.equals("Valid Token")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new JSONObject().put("Authentication Error", token).toString());
        }

        if (!planService.isKeyPresent(type + "_" + objectId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new JSONObject().put("message", "ObjectId does not exist").toString());
        }

        if (type.equals("plan")) {
            existingEtag = planService.getETag(type + "_" + objectId, "eTag");
            String eTag = headers.getFirst("If-None-Match");
            System.out.println("eTAG=> "+eTag);
            System.out.println("existingEtag=> "+existingEtag);
            if (eTag != null && eTag.equals(existingEtag)) {
                return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(existingEtag).build();
            }
        }

        String key = type + "_" + objectId;
        Map<String, Object> outputMap = new HashMap<>();
        outputMap = planService.getDeletePlan(key, outputMap, false);

        if (type.equals("plan")) {
            return ResponseEntity.status(HttpStatus.FOUND).eTag(existingEtag).body(new JSONObject(outputMap).toString());
        }

        return ResponseEntity.status(HttpStatus.FOUND).body(new JSONObject(outputMap).toString());
    }

    @DeleteMapping(value = "/{type}/{objectId}", produces = "application/json")
    public ResponseEntity<Object> deletePlan(@PathVariable String type, @PathVariable String objectId, @RequestHeader HttpHeaders headers) {

        String token = tokenService.authorizeToken(headers);
        if (!token.equals("Valid Token")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new JSONObject().put("Authentication Error", token).toString());
        }

        if (!planService.isKeyPresent(type + "_" + objectId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new JSONObject().put("message", "ObjectId does not exist").toString());
        }
        
        System.out.println("Initital key is ==>" + initialKey);
        System.out.println("2===> " + type + "_" + objectId);
        System.out.println(!initialKey.equals(type + "_" + objectId));
        
        if(!initialKey.equals(type + "_" + objectId)) {
            System.out.println("inside");
            Map<String, Object> outputMap = new HashMap<>();
            outputMap = planService.getDeletePlan(initialKey, outputMap, false);
            planService.createETag(initialKey, new JSONObject(outputMap).toString());
            return ResponseEntity.status(HttpStatus.OK).body(new JSONObject().put("message", type + " is deleted").toString());
        }
        
        Set<String> keys = planDao.getKeys("plan" + "_" + objectId + "*");
        for (String key : keys) {
            if (!key.equals("plan" + "_" + objectId)) {
                Set<String> members = planDao.getAllMembers(key);
                for (String member : members) {
                    messageQueueService.addToMessageQueue(member.substring(member.indexOf("_") + 1), true);
                    Set<String> keys1 = planDao.getKeys(member + "*");
                    for (String key1 : keys1) {
                        if (!key1.equals(member)) {
                            Set<String> members1 = planDao.getAllMembers(key1);
                            for (String member1 : members1) {
                                messageQueueService.addToMessageQueue(member1.substring(member1.indexOf("_") + 1), true);
                            }
                        }
                    }
                }
            }
        }

        planService.getDeletePlan(type + "_" + objectId, null, true); 
        messageQueueService.addToMessageQueue(objectId, true);
        
        return ResponseEntity.status(HttpStatus.OK).body(new JSONObject().put("message", "Plan is deleted").toString());
    }

    @PutMapping(path = "/plan/{objectId}", produces = "application/json")
    public ResponseEntity<Object> updatePlan(@RequestHeader HttpHeaders headers, @RequestBody(required = false) String plan, @PathVariable String objectId) throws IOException {

        if (plan == null || plan.isEmpty()){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new JSONObject().put("message", "Plan is empty").toString());
        }

        String token = tokenService.authorizeToken(headers);
        if (!token.equals("Valid Token")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new JSONObject().put("Authentication Error", token).toString());
        }

        JSONObject planObject = new JSONObject(plan);
        try {
            planValidator.validatePlan(planObject);
        } catch (ValidationException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new JSONObject().put("Validation Error", ex.getMessage()).toString());
        }

        String key = "plan_" + objectId;
        if (!planService.isKeyPresent(key)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new JSONObject().put("Message", "ObjectId does not exist").toString());
        }

        try {
            planService.validateObjectId(planObject, true);
        }catch (NoKeyException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new JSONObject().put("Message", e.getMessage()).toString());
        }

        String existingEtag = planService.getETag("plan_" + objectId, "eTag");
        String eTag = headers.getFirst("If-Match");
        if (eTag != null && !eTag.equals(existingEtag)) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).eTag(existingEtag).build();
        }

        planService.getDeletePlan("plan_" + objectId, null, true);
        planService.addPlanETag(planObject, planObject.getString("objectId").toString());
        Map<String, Object> outputMap = new HashMap<>();
        outputMap = planService.getDeletePlan(key, outputMap, false);

        String newETag = planService.createETag(key, new JSONObject(outputMap).toString());
        //String newEtag1 = planService.savePlanToRedisAndMQ(planObject, key);
        return ResponseEntity.ok().eTag(newETag)
                .body(new JSONObject().put("Message: ", "Plan is updated successfully").toString());
    }

    @PatchMapping(path = "/plan/{objectId}", produces = "application/json")
    public ResponseEntity<Object> patchPlan(@RequestHeader HttpHeaders headers, @RequestBody(required = false) String plan, @PathVariable String objectId) throws IOException {

        if (plan == null || plan.isEmpty()){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new JSONObject().put("message", "Plan is empty").toString());
        }

        String token = tokenService.authorizeToken(headers);
        if (!token.equals("Valid Token")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new JSONObject().put("Authentication Error", token).toString());
        }

        JSONObject planObject = new JSONObject(plan);

        String key = "plan_" + objectId;
        if (!planService.isKeyPresent(key)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new JSONObject().put("Message", "ObjectId does not exist").toString());
        }

        try {
            planService.validateObjectId(planObject, false);
        }catch (NoKeyException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new JSONObject().put("Message", e.getMessage()).toString());
        }

        String existingEtag = planService.getETag("plan_" + objectId, "eTag");
        String eTag = headers.getFirst("If-Match");
        if (eTag != null && !eTag.equals(existingEtag)) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).eTag(existingEtag).build();
        }

        planService.addPlanETag(planObject, planObject.getString("objectId").toString());
        Map<String, Object> outputMap = new HashMap<>();
        outputMap = planService.getDeletePlan(key, outputMap, false);

        String newETag = planService.createETag(key, new JSONObject(outputMap).toString());
       // String newEtag1 = planService.savePlanToRedisAndMQ(planObject, key);
        return ResponseEntity.ok()
                .body(new JSONObject().put("Message: ", "Plan is updated successfully").toString());
    }	
}
