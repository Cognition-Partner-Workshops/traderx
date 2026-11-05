package finos.traderx.tradeservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import finos.traderx.records.AccountReferences;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Account {
    private Integer id;
    private String displayName;

    public Account()
    {


    }

    public Account (Integer id,String displayName)
    {
        this.id = id;
        this.displayName = displayName;
    }

    @JsonDeserialize(using = AccountReferences.Deserializer.class)
    public Integer getid()
    {
        return id;
    }
    public String getdisplayName()
    {
        return displayName;
    }
}
