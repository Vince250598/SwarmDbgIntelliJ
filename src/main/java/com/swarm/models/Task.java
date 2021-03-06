package com.swarm.models;

import com.swarm.utils.HTTPRequest;
import org.json.JSONObject;

import java.util.ArrayList;

public class Task {
    private int id;
    private String title;
    private boolean done;
    private Product product;
    private final ArrayList<Session> sessions = new ArrayList<>();

    public void create() {
        HTTPRequest createTaskRequest = new HTTPRequest();
        createTaskRequest.setQuery("mutation taskCreate($title:String!,$done:Boolean!,$productId:Long!)" +
                "{taskCreate(task:{title:$title,done:$done,product:{id:$productId}}){id}}");
        createTaskRequest.addVariable("title", title);
        createTaskRequest.addVariable("done", done);
        createTaskRequest.addVariable("productId", product.getId());
        JSONObject response = new JSONObject(createTaskRequest.post().getString("body"));

        this.id = response.getJSONObject("data").getJSONObject("taskCreate").getInt("id");
    }

    public void markAsDone() {
        HTTPRequest markAsDoneRequest = new HTTPRequest();
        markAsDoneRequest.setQuery("mutation taskDone($taskId:Long!){taskDone(taskId:$taskId){done}}");
        markAsDoneRequest.addVariable("taskId", this.id);
        JSONObject response = new JSONObject(markAsDoneRequest.post().getString("body"));

        this.done = response.getJSONObject("data").getJSONObject("taskDone").getBoolean("done");
    }


    public int getId() {
        return id;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setDone(boolean done) {
        this.done = done;
    }

    public boolean isDone() {
        return done;
    }

    public ArrayList<Session> getSessions() {
        return sessions;
    }

    public void addSession(Session session) {
        this.sessions.add(session);
    }
}
