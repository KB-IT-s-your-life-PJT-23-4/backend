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
    void registersOnlyCandidatesThatMatchOfficialRiseCatalog() {
        Map<String, String> productTypes = new HashMap<>();
        EtfMarketDataMapper mapper = fakeMapper(productTypes, Map.of("148020", 10L));
        EtfProductMasterRegistrationService service =
                new EtfProductMasterRegistrationService(mapper);

        int registered = service.registerVerifiedRiseProducts(
                List.of(
                        new ExternalEtfProductMaster("148020", "RISE 200", null),
                        new ExternalEtfProductMaster("379780", "RISE 미국S&P500", null),
                        new ExternalEtfProductMaster("999999", "RISE 이름만 같은 ETF", null),
                        new ExternalEtfProductMaster("888888", "RISE 다른 이름", null)
                ),
                Map.of(
                        "148020", new ExternalEtfProductMaster(
                                "148020", "RISE 200", "KB자산운용"),
                        "379780", new ExternalEtfProductMaster(
                                "379780", "RISE 미국S&P500", "KB자산운용"),
                        "888888", new ExternalEtfProductMaster(
                                "888888", "RISE 공식 이름", "KB자산운용")
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
