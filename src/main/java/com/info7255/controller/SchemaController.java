package com.info7255.controller;

import com.info7255.controller.HomeController;

import java.util.Map;

import javax.xml.validation.Validator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;



@RestController
public class SchemaController {
	
	
//	
//	@Autowired
//	private JsonValidator validator;
//	
//	@Autowired
//	private JedisBean jedisBean;
//	
//	@PostMapping("/plan/schema")
//	public ResponseEntity<String> insertSchema(@RequestBody String body, @RequestHeader HttpHeaders requestHeaders) {
//		System.out.println("HELLLLLLLLLLLLLLLLO");
//		HomeController hc = new HomeController();
////		if(!hc.authorize(requestHeaders)) {
////			return new ResponseEntity<String>("Token authorization failed", HttpStatus.NOT_ACCEPTABLE);
////		}
//		if(body == null) {
//			return new ResponseEntity<String>("No Schema received", new HttpHeaders(), HttpStatus.BAD_REQUEST);
//		}
//		if(!JedisBean.insertSchema(body))
//			return new ResponseEntity<String>("Schema insertion failed", new HttpHeaders(), HttpStatus.BAD_REQUEST);
//		
//		validator.refreshSchema();
//		return new ResponseEntity<String>("Schema posted successfully", new HttpHeaders(), HttpStatus.ACCEPTED);
//	}
}
