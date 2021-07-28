package cc.coopersoft.keycloak.phone.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public class JsonUtils {
    private static JsonUtils instance;

    public ObjectMapper mapper;

    public static JsonUtils getInstance(){
        if(instance == null){
            instance = new JsonUtils();
        }
        return instance;
    }

    public JsonUtils(){
        mapper = new ObjectMapper();
    }

    public String encode(Map<String, Object> map) throws JsonProcessingException {
        return mapper.writeValueAsString(map);
    }
}
