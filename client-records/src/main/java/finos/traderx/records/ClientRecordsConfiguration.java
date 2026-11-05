package finos.traderx.records;

import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@AutoConfiguration(after = DataSourceAutoConfiguration.class)
public class ClientRecordsConfiguration {
    public ClientRecordsConfiguration(DataSource dataSource) {
        AccountReferences.initialize(dataSource);
    }

    @Bean
    AccountReferenceLookup accountReferenceLookup() {
        return new AccountReferenceLookup();
    }

    @Bean
    WebMvcConfigurer accountReferencePaths() {
        return new WebMvcConfigurer() {
            @Override
            public void addFormatters(FormatterRegistry registry) {
                registry.addConverter(new AccountReferenceConverter());
            }
        };
    }

    private static class AccountReferenceConverter implements Converter<String, Integer> {
        @Override
        public Integer convert(String value) {
            return AccountReferences.resolve(value);
        }
    }
}
