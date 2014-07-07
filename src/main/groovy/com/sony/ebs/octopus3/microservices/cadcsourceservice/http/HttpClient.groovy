package com.sony.ebs.octopus3.microservices.cadcsourceservice.http

public interface HttpClient {

    rx.Observable<String> getLocal(String url)

    rx.Observable<String> getFromCadc(String url)

    rx.Observable<String> postLocal(String url, String data)

}