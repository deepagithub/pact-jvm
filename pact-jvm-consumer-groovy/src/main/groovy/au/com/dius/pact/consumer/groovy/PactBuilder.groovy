package au.com.dius.pact.consumer.groovy

import au.com.dius.pact.consumer.StatefulMockProvider
import au.com.dius.pact.consumer.VerificationResult
import au.com.dius.pact.model.Consumer
import au.com.dius.pact.model.MockProviderConfig
import au.com.dius.pact.model.MockProviderConfig$
import au.com.dius.pact.model.PactFragment
import au.com.dius.pact.model.Provider
import au.com.dius.pact.model.Request$
import au.com.dius.pact.model.Response$
import au.com.dius.pact.model.Interaction$
import groovy.json.JsonBuilder
import org.json.JSONObject
import scala.None$
import scala.collection.JavaConverters$

class PactBuilder {

    Consumer consumer
    Provider provider
    Integer port = null
    String requestDescription
    List requestData = []
    List responseData = []
    List interactions = []
    StatefulMockProvider server

    def call(Closure closure) {
        build(closure)
    }

    def build(Closure closure) {
        closure.delegate = this
        closure.call()
    }

    PactBuilder service_consumer(String consumer) {
        this.consumer = new Consumer(consumer)
        this
    }

    PactBuilder has_pact_with(String provider) {
        this.provider = new Provider(provider)
        this
    }

    PactBuilder port(int port) {
        this.port = port
        this
    }

    PactBuilder upon_receiving(String requestDescription) {
        buildInteractions()
        this.requestDescription = requestDescription
        this
    }

    def buildInteractions() {
        int numInteractions = Math.min(requestData.size(), responseData.size())
        for (int i = 0; i < numInteractions; i++) {
            Map headers = requestData[i].headers ?: [:]
            Map responseHeaders = responseData[i].headers ?: [:]
            interactions << Interaction$.MODULE$.apply(
                    requestDescription,
                    None$.apply(""),
                    Request$.MODULE$.apply(requestData[i].method ?: 'get', requestData[i].path ?: '/', headers,
                            requestData[i].body ?: '', new JSONObject()),
                    Response$.MODULE$.apply(responseData[i].status ?: 200, responseHeaders,
                            responseData[i].body ?: '', new JSONObject())
            )
        }
        requestData = []
        responseData = []
    }

    PactBuilder with(Map requestData) {
        def request = [:] + requestData
        def body = requestData.body
        if (body != null && !(body instanceof String)) {
            request.body = new JsonBuilder(body).toPrettyString()
        }
        this.requestData << request
        this
    }

    PactBuilder will_respond_with(Map responseData) {
        def response = [:] + responseData
        def body = responseData.body
        if (body != null && !(body instanceof String)) {
            response.body = new JsonBuilder(body).toPrettyString()
        }
        this.responseData << response
        this
    }

    VerificationResult run(Closure closure) {
        buildInteractions()
        def fragment = new PactFragment(consumer, provider, JavaConverters$.MODULE$.asScalaBufferConverter(interactions).asScala())

        MockProviderConfig config
        if (port == null) {
            config = MockProviderConfig.createDefault()
        } else {
            config = MockProviderConfig$.MODULE$.apply(port, 'localhost')
        }

        fragment.runConsumer(config, closure)
    }
}