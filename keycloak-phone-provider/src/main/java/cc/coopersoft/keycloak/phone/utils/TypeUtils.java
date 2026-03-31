package cc.coopersoft.keycloak.phone.utils;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import jakarta.ws.rs.core.MultivaluedMap;

public class TypeUtils {
    public static Map<String, String> multivaluedMapToMap(MultivaluedMap<String, String> multiValuedMap) {
        Map<String, String> map = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : multiValuedMap.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                map.put(entry.getKey(), entry.getValue().get(0));
            }
        }
        return map;
    }
}
