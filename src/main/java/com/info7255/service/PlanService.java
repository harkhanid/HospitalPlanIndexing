package com.info7255.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.apache.commons.codec.digest.DigestUtils;
import com.info7255.dao.PlanDao;
import com.info7255.exception.NoKeyException;

import redis.clients.jedis.exceptions.JedisException;

@Service
public class PlanService {
	
	 @Autowired
	 PlanDao planDao;
	 
	 @Autowired
	 MessageQueue messageQueue;
	 
	 //test
	 private Map<String,String> relationMap = new HashMap<>();
	 
	public boolean isKeyPresent(String key) {
		return planDao.isKeyPresent(key);
	};

	public String addPlanETag(JSONObject planObject, String objectID) {

        //save json object
        Map<String, Object> savedPlanMap = savePlan(objectID, planObject);
        String savedPlan = new JSONObject(savedPlanMap).toString();

        indexQueue(planObject, objectID);

        //create and save eTag
        String newEtag = DigestUtils.md5Hex(savedPlan);
        System.out.println("eTag: " + newEtag);
        planDao.setHashValue(objectID, "eTag", newEtag);

        return newEtag;
    }
	public Map<String, Object> savePlan(String key, JSONObject planObject) {
  //      Map<String, Map<String, Object>> outputMap = new HashMap<>();
//        Map<String, Object> valueMap = new HashMap<>();
//
//        Iterator<String> keyIterator = planObject.keySet().iterator();
//        while (keyIterator.hasNext()) {
//            String redisKey = planObject.get("objectType") + "_" + planObject.get("objectId");
//            String key = keyIterator.next();
//            Object val = planObject.get(key);
//            System.out.println("value is " + val);
//            System.out.println("key is " + key);
//            System.out.println("redis key is " + redisKey);
//            if (val instanceof JSONObject) {
//                System.out.println("inside 1");
//                val = savaPlan((JSONObject) val);
//                HashMap<String, Map<String, Object>> value = (HashMap<String, Map<String, Object>>) val;
//                planDao.setKeyValue(redisKey + "_" + key, value.entrySet().iterator().next().getKey());
//            } else if (val instanceof JSONArray) {
//                System.out.println("inside 2");
//                val = convertToList((JSONArray) val);
//                for (HashMap<String, HashMap<String, Object>> entrySet : (List<HashMap<String, HashMap<String, Object>>>) val) {
//                    for (String listKey : entrySet.keySet()) {
//                        planDao.setKeyValue(redisKey + "_" + key, listKey);
//                    }
//                }
//            } else {
//                planDao.setHashValue(redisKey, key, val.toString());
//                System.out.println("hash val " + redisKey + "~" + key + "~" + val.toString());
//                valueMap.put(key, val);
//                outputMap.put(redisKey, valueMap);
//            }
//            System.out.println("-------------------------------");
//        }
//        return outputMap;
		//test
		store(planObject);
        //step2: fetch, organize and operate all Hash as the request
        Map<String, Object> outputMap = new HashMap<>();
        processNestedJSONObject(key, outputMap, false);

        return outputMap;
        
    }
	//test
	public Map<String, Object> getPlan(String key){
        Map<String, Object> outputMap = new HashMap<>();
        processNestedJSONObject(key, outputMap, false);
        return outputMap;
	}
	//test
	public void deletePlan(String key) {
        processNestedJSONObject(key, null, true);
    }
	//test
	private void indexQueue(JSONObject jsonObject, String uuid) {

        try {

            Map<String,String> simpleMap = new HashMap<>();


            for(Object key : jsonObject.keySet()) {
                String attributeKey = String.valueOf(key);
                Object attributeVal = jsonObject.get(String.valueOf(key));
                String edge = attributeKey;

                if(attributeVal instanceof JSONObject) {
                    JSONObject embdObject = (JSONObject) attributeVal;

                    JSONObject joinObj = new JSONObject();
                    if(edge.equals("planserviceCostShares") && embdObject.getString("objectType").equals("membercostshare")){
                        joinObj.put("name", "planservice_membercostshare");
                    } else {
                        joinObj.put("name", embdObject.getString("objectType"));
                    }

                    joinObj.put("parent", uuid);
                    embdObject.put("plan_service", joinObj);
                    embdObject.put("parent_id", uuid);
                    System.out.println(embdObject.toString());
                    messageQueue.addToMessageQueue(embdObject.toString(), false);

                } else if (attributeVal instanceof JSONArray) {

                    JSONArray jsonArray = (JSONArray) attributeVal;
                    Iterator<Object> jsonIterator = jsonArray.iterator();

                    while(jsonIterator.hasNext()) {
                        JSONObject embdObject = (JSONObject) jsonIterator.next();
                        embdObject.put("parent_id", uuid);
                        System.out.println(embdObject.toString());

                        String embd_uuid = embdObject.getString("objectId");
                        relationMap.put(embd_uuid, uuid);

                        indexQueue(embdObject, embd_uuid);
                    }

                } else {
                    simpleMap.put(attributeKey, String.valueOf(attributeVal));
                }
            }

            JSONObject joinObj = new JSONObject();
            joinObj.put("name", simpleMap.get("objectType"));

            if(!simpleMap.containsKey("planType")){
                joinObj.put("parent", relationMap.get(uuid));
            }

            JSONObject obj1 = new JSONObject(simpleMap);
            obj1.put("plan_service", joinObj);
            obj1.put("parent_id", relationMap.get(uuid));
            System.out.println(obj1.toString());
            messageQueue.addToMessageQueue(obj1.toString(), false);


        }
        catch(JedisException e) {
            e.printStackTrace();
        }
    }

    //test
    private Map<String, Map<String, Object>> store(JSONObject object) {

        Map<String, Map<String, Object>> map = new HashMap<>();
        Map<String, Object> valueMap = new HashMap<>();
        //get all keys from the object
        Iterator<String> iterator = object.keySet().iterator();
        System.out.println("iterators: " + object.keySet().toString());//TODO:only for testing

        while (iterator.hasNext()) {
            //store as {redisKey, key, value} | redisKey: "objectType_objectID"
            String redisKey = object.get("objectType") + "_" + object.get("objectId");
            String key = iterator.next();
            Object value = object.get(key);

            //save values with various types: object, array, string
            //1. save object: e.g. planCostShares:{k-v}
            if (value instanceof JSONObject) {
                value = store((JSONObject) value);
                //E.g. <membercostshare_1234xxxxx-501, <5 kv-pairs>>
                HashMap<String, Map<String, Object>> val = (HashMap<String, Map<String, Object>>) value;
                //save set (redisKey_key, objectID),e.g. (redisKey_planCostShares, membercostshare_1234xxxxx-501);
                planDao.setKeyValue(redisKey + "_" + key, val.entrySet().iterator().next().getKey());


            //2. save list: e.g. linkedPlanServices:[{k-v},{k-v}]
            } else if (value instanceof JSONArray) {
                value = convertToList((JSONArray) value);
                for (HashMap<String, HashMap<String, Object>> entry : (List<HashMap<String, HashMap<String, Object>>>) value) {
                    for (String listKey : entry.keySet()) {
                        //save 2 linkedPlanServices
                        planDao.setKeyValue(redisKey + "_" + key, listKey);
                    }
                }

            //3. save string: e.g. {objectType: plan}
            } else {
                planDao.setHashValue(redisKey, key, value.toString());
                valueMap.put(key, value);
                map.put(redisKey, valueMap);

            }
        }
        System.out.println("map: "+ map.toString());
        return map;

    }
    //test
    private Map<String, Object> processNestedJSONObject(String redisKey, Map<String, Object> outputMap, boolean isDelete) {

        Set<String> keys = planDao.getKeys(redisKey + "*");
        for (String key : keys) {

            //Case 1. KV pairs with redisKey
            if (key.equals(redisKey)) {
                if (isDelete) {
                    //1. delete plan
                    planDao.deleteKeys(new String[] {key});
                } else {
                    //2. get kv pairs stored
                    Map<String, String> val = planDao.getAllValuesByKey(key);
                    for (String name : val.keySet()) {
                        //put kv into outMap except eTag
                        if (!name.equalsIgnoreCase("eTag")) {
                            outputMap.put(name, isDouble(val.get(name)) ? Double.parseDouble(val.get(name)) : val.get(name));
                        }
                    }
                }

            //operate on sub-objects
            } else {

                //get the key for sub-object
                String curKey = key.substring((redisKey + "_").length());
                //get all keys
                Set<String> members = planDao.getAllMembers(key);

                //Case 2. Nested Object List
                if (members.size() > 1) {
                    List<Object> listObj = new ArrayList<>();

                    //recursion to the kv-pairs level for data operation
                    for (String member : members) {
                        if (isDelete) {
                            processNestedJSONObject(member, null, true);
                        } else {
                            Map<String, Object> listMap = new HashMap<>();
                            listObj.add(processNestedJSONObject(member, listMap, false));

                        }
                    }
                    if (isDelete) {
                        planDao.deleteKeys(new String[] {key});
                    } else {
                        outputMap.put(curKey, listObj);
                    }

                //Case 3. Nested Object
                } else {
                    if (isDelete) {
                        planDao.deleteKeys(new String[]{members.iterator().next(), key});
                    } else {
                        Map<String, String> val = planDao.getAllValuesByKey(members.iterator().next());
                        Map<String, Object> newMap = new HashMap<>();
                        for (String name : val.keySet()) {
                            newMap.put(name, isDouble(val.get(name)) ? Double.parseDouble(val.get(name)) : val.get(name));
                        }
                        outputMap.put(curKey, newMap);
                    }
                }
            }
        }
        return outputMap;
    }
	
    public String createETag(String key, String value) {
    	String eTag = DigestUtils.md5Hex(value);
        planDao.setHashValue(key, "eTag", eTag);
        return eTag;
    }

    public Map<String, Object> getDeletePlan(String redisKey, Map<String, Object> outputMap, boolean isDelete) {
        Set<String> keys = planDao.getKeys(redisKey + "*");
        for (String key : keys) {
            if (key.equals(redisKey)) {
                if (isDelete) {
                    planDao.deleteKeys(new String[] {key});
                } else {
                    Map<String, String> val = planDao.getAllValuesByKey(key);
                    for (String name : val.keySet()) {
                        if (!name.equalsIgnoreCase("eTag")) {
                            outputMap.put(name, isDouble(val.get(name)) ? Double.parseDouble(val.get(name)) : val.get(name));
                        }
                    }
                }
            } else {
                String str = key.substring((redisKey + "_").length());
                System.out.println("key is==> " + key);
                Set<String> membersSet = planDao.getAllMembers(key);
                System.out.println("memberset==> " + membersSet.size());
                System.out.println("memberset==> " + membersSet);

                /*for (String member : membersSet) {
                    if (isDelete) {
                        getDeletePlan(member, null, true);
                    } else {
                        Map<String, Object> listMap = new HashMap<>();
                        Map<String, Object> abc = getDeletePlan(member, listMap, false);
                        System.out.println("abc is==>" + abc);
                        outputMap.put(str, abc);
                        System.out.println("output top==> " + outputMap);
                        Map<String, String> val = planDao.getAllValuesByKey(member);
                        System.out.println("val is==> " + val);
                        Map<String, Object> newMap = new HashMap<>();
                        for (String name : val.keySet()) {
                            System.out.println("name is==> " + name);
                            newMap.put(name, isDouble(val.get(name)) ? Double.parseDouble(val.get(name)) : val.get(name));
                        }
                        System.out.println("str is==> " + str);
                        System.out.println("new map is==>" + newMap);
                        outputMap.put(str, newMap);
                        System.out.println("output bottom==> " + outputMap);
                    }
                }*/


                if (membersSet.size() > 1) {
                    System.out.println("inside 3");
                    List<Object> listObj = new ArrayList<>();
                    for (String member : membersSet) {
                        System.out.println("member is " + member);
                        if (isDelete) {
                            getDeletePlan(member, null, true);
                        } else {
                            Map<String, Object> listMap = new HashMap<>();
                            listObj.add(getDeletePlan(member, listMap, false));
                            System.out.println("listObj is==>" + listObj);
                        }
                    }

                    if (isDelete) {
                        planDao.deleteKeys(new String[] {key});
                    } else {
                        outputMap.put(str, listObj);
                    }
                    System.out.println("inside output map is==>" + outputMap);
                } else {
                    System.out.println("inside 4");
                    if (isDelete) {
                        planDao.deleteKeys(new String[]{membersSet.iterator().next(), key});
                    } else {

                        /*List<Object> listObj1 = new ArrayList<>();
                        for (String member : membersSet) {
                            System.out.println("member inside is " + member);
                            if (isDelete) {
                                getDeletePlan(member, null, true);
                            } else {
                                Map<String, Object> listMap = new HashMap<>();
                                listObj1.add(getDeletePlan(member, listMap, false));
                                System.out.println("listObj1 is==>" + listObj1);
                            }
                        }
                        if (isDelete) {
                            planDao.deleteKeys(new String[] {key});
                        } else {
                            outputMap.put(str, listObj1);
                        }
                        System.out.println("inside output map1 is==>" + outputMap);*/

                        Map<String, String> val = planDao.getAllValuesByKey(membersSet.iterator().next());
                        System.out.println("val is==> " + val);
                        Map<String, Object> newMap = new HashMap<>();
                        for (String name : val.keySet()) {
                            System.out.println("name is==> " + name);
                            newMap.put(name, isDouble(val.get(name)) ? Double.parseDouble(val.get(name)) : val.get(name));
                        }
                        outputMap.put(str, newMap);
                        System.out.println("inside output map is1111==>" + outputMap);
                    }
                }
            }
        }
        System.out.println("OUTPUT===> " + outputMap);
        return outputMap;
    }

    public String getETag(String key, String field) {
    	return planDao.getHashValue(key, field);
    }

    public Map<String, Map<String, Object>> validateObjectId(JSONObject planObject, boolean isPut) throws NoKeyException {
        Map<String, Map<String, Object>> outputMap = new HashMap<>();
        Map<String, Object> valueMap = new HashMap<>();

        Iterator<String> keyIterator = planObject.keySet().iterator();
        while (keyIterator.hasNext()) {
            String redisKey = planObject.get("objectType") + "_" + planObject.get("objectId");
            String key = keyIterator.next();
            Object val = planObject.get(key);
            System.out.println("value is " + val);
            System.out.println("key is " + key);
            System.out.println("redis key is " + redisKey);

            if(planDao.isKeyPresent(redisKey)) {
                System.out.println("------------------");
                System.out.println(redisKey + "--->> TRUE");
                System.out.println("------------------");
            }else {
                System.out.println("------------------");
                System.out.println(redisKey + "--->> FALSE");
                System.out.println("------------------");
                throw new NoKeyException(redisKey + " is not present");
            }

            if(!isPut) {
                if(key.equals("planCostShares")) {
                    if(!isPlanCostSharesDeleted(redisKey)) {
                        if (val instanceof JSONObject) {
                            //System.out.println("inside 1");
                            val = validateObjectId((JSONObject) val, isPut);
                            //System.out.println("---****>> "+ redisKey + "_" + key);
                            if(planDao.isKeyPresent(redisKey + "_" + key)) {
                                System.out.println("------------------");
                                System.out.println(redisKey + "_" + key + "--->> TRUE");
                                System.out.println("------------------");
                            }else {
                                System.out.println("------------------");
                                System.out.println(redisKey + "_" + key + "--->> FALSE");
                                System.out.println("------------------");
                                throw new NoKeyException(redisKey + " is not present");
                            }
                        }
                    }
                }
            }
//            Set<String> keys = planDao.getKeys(redisKey + "*");
//            System.out.println("===>>> " + keys);
//            for (String key1 : keys) {
//                System.out.println("===>>> " + key1);
//            }
        }
        return null;
    }

    

    
    
    public boolean isPlanCostSharesDeleted(String key) {
    	Set<String> stringSet;
        stringSet = planDao.getAllMembers(key + "_planCostShares");
        if(!stringSet.isEmpty()) {
            Map<String,String> stringMap = planDao.getAllValuesByKey(stringSet.iterator().next());
            if(!stringMap.isEmpty()) {
                return false;
            }
        }
        return true;
    }
    
    //changed savePlan to store
    private List<Object> convertToList(JSONArray planArray) {
        List<Object> planList = new ArrayList<>();
        for (int i = 0; i < planArray.length(); i++) {
            Object val = planArray.get(i);
            if (val instanceof JSONArray) {
                val = convertToList((JSONArray) val);
            } else if (val instanceof JSONObject) {
                val = store((JSONObject) val);
            }
            planList.add(val);
        }
        return planList;
    }

    private boolean isDouble(String s) {
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }
    
    public void hSet(String key, String field, String value ) {
        planDao.setHashValue(key, field, value);
    }
   
//    public String savePlanToRedisAndMQ(JSONObject planObject, String key) {
//        Map<String, Object> savedPlanMap = savaPlan(planObject);
//        String savedPlan = new JSONObject(savedPlanMap).toString();
//
//        // save plan to MQ
//        messageQueue.addToMessageQueue(planObject.toString(), false);
//
//        String newEtag = DigestUtils.md5Hex(savedPlan);
//        hSet(key, "eTag", newEtag);
//        return newEtag;
//    }
}
