package com.example.project.batch.product.etf.service;

import com.example.project.batch.product.etf.domain.ExternalEtfProductMaster;
import com.example.project.batch.product.etf.mapper.EtfMarketDataMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EtfProductMasterRegistrationServiceTest {

    @Test
    void registersOnlyRiseProductsOperatedByKbAssetManagement() {
        Map<String, String> productTypes = new HashMap<>();
        EtfMarketDataMapper mapper = fakeMapper(productTypes, Map.of("069500", 10L));
        EtfProductMasterRegistrationService service =
                new EtfProductMasterRegistrationService(mapper);

        int registered = service.registerVerifiedRiseProducts(List.of(
                new ExternalEtfProductMaster("069500", "RISE 코리아200", "KB자산운용"),
                new ExternalEtfProductMaster("379780", "RISE 미국S&P500", "KB자산운용"),
                new ExternalEtfProductMaster("999999", "RISE 이름만 같은 ETF", "다른자산운용"),
                new ExternalEtfProductMaster("888888", "다른 ETF", "KB자산운용")
        ));

        assertEquals(1, registered);
        assertEquals("ETF", productTypes.get("379780"));
        assertEquals(1, productTypes.size());
    }

    private EtfMarketDataMapper fakeMapper(
            Map<String, String> productTypes,
            Map<String, Long> existingEtfs
    ) {
        return (EtfMarketDataMapper) Proxy.newProxyInstance(
                EtfMarketDataMapper.class.getClassLoader(),
                new Class<?>[]{EtfMarketDataMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "selectProductIdByEtfStockCode" -> existingEtfs.get((String) args[0]);
                    case "selectProductTypeByCode" -> productTypes.get((String) args[0]);
                    case "insertEtfProductMaster" -> {
                        productTypes.put((String) args[0], "ETF");
                        yield 1;
                    }
                    default -> defaultValue(method.getReturnType());
                });
    }

    private Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == long.class) {
            return 0L;
        }
        return 0;
    }
}
