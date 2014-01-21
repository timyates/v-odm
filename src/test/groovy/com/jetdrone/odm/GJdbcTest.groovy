package com.jetdrone.odm

import org.junit.Test

import org.vertx.groovy.core.Vertx
import org.vertx.groovy.platform.Container

import org.vertx.testtools.TestVerticle

import static org.vertx.testtools.VertxAssert.*

class GJdbcTest extends TestVerticle {

    Map config = [ address: "com.bloidonia.jdbcpersistor" ]

    GJdbcPersons mapper

    @Test
    void createSingleInstance() {
        // using the java inner class way
        Person person = mapper.newRecord()
        assertNotNull(person)
        testComplete()
    }

    @Test
    void saveNewInstance() {
        // using the java inner class way
        Person person = mapper.newRecord()
        assertNotNull(person)

        // set some fields
        person["NAME"] = "Paulo"
        person["AGE"] = 33
        // there is no id
        assertNull(person[mapper.ID])
        // save
        person.save() { saved ->
            if (!saved) {
                fail()
            }
            else {
	            // user has been saved, the ID should be filled now
	            assertNotNull(person[ mapper.ID ])
	            testComplete()
            }
        }
    }

    @Override
    void start() {
        def gvertx = new Vertx( vertx )
        def gcontainer = new Container( container )
        initialize()
        final String address = config.address

        gcontainer.deployModule("com.bloidonia~mod-jdbc-persistor~2.1", config ) { asyncResult ->
            if (asyncResult.failed()) {
                gcontainer.logger.error(asyncResult.cause())
            }
            assertTrue(asyncResult.succeeded())
            assertNotNull("deploymentID should not be null", asyncResult.result())

            Map initdb = [ action: 'execute',
                           stmt: '''CREATE TABLE IF NOT EXISTS users
                                   |    ( ID INTEGER GENERATED BY DEFAULT AS IDENTITY (START WITH 1 INCREMENT BY 1) NOT NULL,
                                   |      NAME varchar(255),
                                   |      AGE integer,
                                   |      CONSTRAINT personid PRIMARY KEY ( ID ) )'''.stripMargin() ]

            gvertx.eventBus.send(address, initdb) { event ->
                assertEquals("ok", event.body.status)
                this.mapper = new GJdbcPersons(gvertx.eventBus, address)
        	    startTests()
            }
        }
    }
}
