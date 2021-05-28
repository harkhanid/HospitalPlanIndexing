package com.info7255.validator;

import java.io.IOException;
import java.io.InputStream;

import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.springframework.stereotype.Service;

@Service
public class SchemaValidator {
	public void validatePlan(JSONObject planObject) throws IOException, ValidationException {
        try(InputStream inputStream = getClass().getResourceAsStream("/PlanSchema.json")){
            JSONObject jsonSchema = new JSONObject(new JSONTokener(inputStream));
            Schema schema = SchemaLoader.load(jsonSchema);
            schema.validate(planObject);
        }
    }

}
