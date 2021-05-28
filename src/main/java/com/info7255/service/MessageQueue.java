package com.info7255.service;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.info7255.dao.MessagingDao;
import com.info7255.dao.PlanDao;

@Service
public class MessageQueue {
	@Autowired
	private MessagingDao messageQueueDao;

	public void addToMessageQueue(String message, boolean isDelete) {
		JSONObject object = new JSONObject();
		object.put("message", message);
		object.put("isDelete", isDelete);

		// save plan to message queue "messageQueue"
		messageQueueDao.addToQueue("messageQueue", object.toString());
		System.out.println("Message saved successfully: " + object.toString());
	}
}
