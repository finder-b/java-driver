/*
 *      Copyright (C) 2012-2015 DataStax Inc.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package com.datastax.driver.core;

import java.util.*;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

import com.datastax.driver.core.utils.CassandraVersion;

/**
 * This tests that the CodecRegistry is able to create codecs on the fly
 * for nested UDTs and tuples received from a ResultSet, i.e. the first time
 * the registry encounters the UDT or tuple, it's coming from a ResultSet,
 * not from the Cluster's metadata.
 *
 * @jira_ticket JAVA-847
 */
@CassandraVersion(major = 2.1)
public class TypeCodecNestedUDTAndTupleIntegrationTest extends CCMBridge.PerClassSingleNodeCluster {

    @Override
    protected Collection<String> getTableDefinitions() {
        return Lists.newArrayList(
            "CREATE TYPE IF NOT EXISTS \"udt2\" (f2 text)",
            "CREATE TYPE IF NOT EXISTS \"udt1\" (f1 frozen<udt2>)",
            "CREATE TABLE IF NOT EXISTS \"t1\" (pk int PRIMARY KEY, c1 frozen<udt1>, c2 frozen<tuple<tuple<text>>>)",
            // it's important to insert values using CQL literals
            // so that the CodecRegistry will not be required until
            // we receive a ResultSet
            "INSERT INTO t1 (pk, c1) VALUES (1, {f1:{f2:'foo'}})",
            "INSERT INTO t1 (pk, c2) VALUES (2, (('foo')))"
        );
    }

    @Test(groups = "short")
    public void should_set_registry_on_nested_udts() {
        ResultSet rows = session.execute("SELECT c1 FROM t1 WHERE pk = 1");
        Row row = rows.one();
        // here the CodecRegistry will create a codec on-the-fly using the UserType received from the resulset metadata
        UDTValue udt1 = row.getUDTValue("c1");
        UDTValue udt2 = udt1.getUDTValue("f1");
        String f2 = udt2.getString("f2");
        assertThat(f2).isEqualTo("foo");
    }

    @Test(groups = "short")
    public void should_set_registry_on_nested_tuples() {
        ResultSet rows = session.execute("SELECT c2 FROM t1 WHERE pk = 2");
        Row row = rows.one();
        // here the CodecRegistry will create a codec on-the-fly using the TupleType received from the resulset metadata
        TupleValue tuple1 = row.getTupleValue("c2");
        TupleValue tuple2 = tuple1.getTupleValue(0);
        String s = tuple2.getString(0);
        assertThat(s).isEqualTo("foo");
    }


}
