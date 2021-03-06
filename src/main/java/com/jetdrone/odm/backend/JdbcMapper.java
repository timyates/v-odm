package com.jetdrone.odm.backend;

import com.jetdrone.odm.Mapper;
import com.jetdrone.odm.Record;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public abstract class JdbcMapper<R extends Record<Number>> extends Mapper<Number, R, String> {

    private static final String ALL = "*";

    public JdbcMapper(EventBus eventBus, String address, String entity) {
        super(eventBus, address, entity, "ID");
    }

    private String fields(R record) {
        StringBuilder sb = new StringBuilder();
        Set<String> fields = record.getFieldNames();
        if (fields != null) {
            int len = fields.size();
            for (String f : fields) {
                sb.append(f);
                if (0 != --len) {
                    sb.append(", ");
                }
            }
        }
        return sb.toString();
    }

    private String placeholders(R record, boolean names) {
        StringBuilder sb = new StringBuilder();
        Set<String> fields = record.getFieldNames();
        if (fields != null) {
            int len = fields.size();
            for (String f : fields) {
                if( names ) {
                    sb.append(f).append("=");
                }
                sb.append("?");
                if (0 != --len) {
                    sb.append(", ");
                }
            }
        }
        return sb.toString();
    }

    private JsonArray values(R record) {
        List ret = new ArrayList();
        Set<String> fields = record.getFieldNames();
        if (fields != null) {
            for (String f : fields) {
                ret.add(record.getValue(f));
            }
        }
        return new JsonArray(ret);
    }

    @Override
    public void find(final String query, final AsyncResultHandler<List<R>> callback) {
        final JsonObject json = new JsonObject()
                .putString("action", "select")
                .putString("stmt", "SELECT " + ALL + " FROM " + entity + " " + query);

        eventBus.send(address, json, new Handler<Message<JsonObject>>() {

            List<R> retValue = new ArrayList<>();

            @Override
            public void handle(Message<JsonObject> event) {
                final String status = event.body().getString("status");

                if ("error".equals(status)) {
                    callback.handle(wrapResult(new RuntimeException(event.body().getString("message")), (List<R>) null));
                    return;
                }
                // convert from json back to R
                JsonArray results = event.body().getArray("result");
                if (results.size() == 0) {
                    retValue.add(null);
                }
                for (Object result : results) {
                    retValue.add(newRecord((JsonObject) result));
                }
                callback.handle(wrapResult(null, retValue));
            }
        });
    }

    @Override
    public void update(final String query, final R record, final AsyncResultHandler<Void> callback) {
        final String sql;

        try {
            sql = "UPDATE " + entity + " SET " + placeholders(record,true) + " " + query;
        } catch (RuntimeException re) {
            callback.handle(wrapResult(re, (Void) null));
            return;
        }

        final JsonObject json = new JsonObject()
                .putString("action", "update")
                .putString("stmt", sql)
                .putArray("values", values(record));

        eventBus.send(address, json, new Handler<Message<JsonObject>>() {
            @Override
            public void handle(Message<JsonObject> event) {
                final String status = event.body().getString("status");

                if ("error".equals(status)) {
                    callback.handle(wrapResult(new RuntimeException(event.body().getString("message")), (Void) null));
                    return;
                }

                callback.handle(wrapResult(null, (Void) null));
            }
        });
    }

    @Override
    public void save(final R record, final AsyncResultHandler<Number> callback) {
        final String sql;

        try {
            sql = "INSERT INTO " + entity + " (" + fields(record) + ") VALUES (" + placeholders(record,false) + ")";
        } catch (RuntimeException re) {
            callback.handle(wrapResult(re, (Number) null));
            return;
        }

        final JsonObject json = new JsonObject()
                .putString("action", "insert")
                .putString("stmt", sql)
                .putArray("values", values(record));

        eventBus.send(address, json, new Handler<Message<JsonObject>>() {
            @Override
            public void handle(Message<JsonObject> event) {
                final String status = event.body().getString("status");

                if ("error".equals(status)) {
                    callback.handle(wrapResult(new RuntimeException(event.body().getString("message")), (Number) null));
                    return;
                }

                if ("ok".equals(status)) {
                    // Ids have been returned
                    Number id = ((JsonObject) event.body().getArray("result").get(0)).getNumber(ID) ;
                    callback.handle(wrapResult(null, id));
                }
            }
        });
    }

    @Override
    public void findOne(final String query, final AsyncResultHandler<R> callback) {
        final JsonObject json = new JsonObject()
                .putString("action", "select")
                .putString("stmt", "SELECT " + ALL + " FROM " + entity + " " + query);

        eventBus.send(address, json, new Handler<Message<JsonObject>>() {
            @Override
            public void handle(Message<JsonObject> event) {
                final String status = event.body().getString("status");

                if ("error".equals(status)) {
                    callback.handle(wrapResult(new RuntimeException(event.body().getString("message")), (R) null));
                    return;
                }

                // convert from json back to R
                JsonArray results = event.body().getArray("result");

                JsonObject data = null;

                if (results.size() >= 1) {
                    data = (JsonObject) results.get(0);
                }

                callback.handle(wrapResult(null, newRecord(data)));
            }
        });
    }

    @Override
    public void remove(final String query, final AsyncResultHandler<Void> callback) {
        final JsonObject json = new JsonObject()
                .putString("action", "execute")
                .putString("stmt", "DELETE FROM " + entity + " " + query);

        eventBus.send(address, json, new Handler<Message<JsonObject>>() {
            @Override
            public void handle(Message<JsonObject> event) {
                final String status = event.body().getString("status");

                if ("error".equals(status)) {
                    callback.handle(wrapResult(new RuntimeException(event.body().getString("message")), (Void) null));
                    return;
                }

                if ("ok".equals(status)) {
                    callback.handle(wrapResult(null, (Void) null));
                }
            }
        });
    }

    @Override
    public void count(final String query, final AsyncResultHandler<Number> callback) {
        final JsonObject json = new JsonObject()
                .putString("action", "select")
                .putString("stmt", "SELECT COUNT(*) AS CNT FROM " + entity + " " + query);

        eventBus.send(address, json, new Handler<Message<JsonObject>>() {
            @Override
            public void handle(Message<JsonObject> event) {
                final String status = event.body().getString("status");

                if ("error".equals(status)) {
                    callback.handle(wrapResult(new RuntimeException(event.body().getString("message")), (Number) null));
                    return;
                }

                // counts have been returned
                Number count = ((JsonObject) event.body().getArray("result").get(0)).getNumber("CNT");

                callback.handle(wrapResult(null, count));
            }
        });
    }

    @Override
    public void truncate(final AsyncResultHandler<Void> callback) {
        final JsonObject json = new JsonObject()
                .putString("action", "execute")
                .putString("stmt", "TRUNCATE TABLE " + entity);

        eventBus.send(address, json, new Handler<Message<JsonObject>>() {
            @Override
            public void handle(Message<JsonObject> event) {
                final String status = event.body().getString("status");

                if ("error".equals(status)) {
                    callback.handle(wrapResult(new RuntimeException(event.body().getString("message")), (Void) null));
                    return;
                }

                callback.handle(wrapResult(null, (Void) null));
            }
        });
    }

    @Override
    public void update(final R record, final AsyncResultHandler<Void> callback) {
        update("WHERE " + ID + " = " + record.getId(), record, callback);
    }

    @Override
    public void remove(final R record, final AsyncResultHandler<Void> callback) {
        remove("WHERE " + ID + " = " + record.getId(), callback);
    }

    @Override
    public void findById(Number id, final AsyncResultHandler<R> callback) {
        findOne("WHERE " + ID + " = " + id, callback);
    }

    @Override
    public void findAll(final AsyncResultHandler<List<R>> callback) {
        find("", callback);
    }

    @Override
    public void count(final AsyncResultHandler<Number> callback) {
        count("", callback);
    }
}
