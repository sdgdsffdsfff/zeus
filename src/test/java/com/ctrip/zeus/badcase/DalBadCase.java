package com.ctrip.zeus.badcase;

import com.ctrip.zeus.model.entity.Badcasetest;
import com.ctrip.zeus.model.transform.DefaultJsonBuilder;
import com.ctrip.zeus.model.transform.DefaultJsonParser;
import org.junit.Test;

import java.io.IOException;

/**
 * Created by fanqq on 2015/3/30.
 */
public class DalBadCase {

    @Test
    public void badcase1() throws IOException {
        Badcasetest badcasetest = new Badcasetest();
        badcasetest.addStringlist("test1").addStringlist("test1")
                    .addStringlist2("test2").addStringlist2("test2");

        String json = new DefaultJsonBuilder().build(badcasetest);
        String json2 = String.format(Badcasetest.JSON,badcasetest);

        //json == json2
        //got exception
        badcasetest = DefaultJsonParser.parse(Badcasetest.class,json);
        badcasetest = DefaultJsonParser.parse(Badcasetest.class,json2);



    }
}
