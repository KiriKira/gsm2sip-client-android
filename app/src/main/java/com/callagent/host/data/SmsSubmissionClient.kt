package com.callagent.host.data

/** The two network operations available to the SMS composer and its saved tasks. */
internal interface SmsSubmissionClient {
    fun createMessage(envelope: RetryEnvelope): MessageAccepted
    fun getMessage(messageId: String): SmsRecord?
}

/** Captures an already account-bound API before work enters the Activity's executor. */
internal object SmsSubmissionClients {
    @Volatile
    internal var factoryOverride: ((ApiClient) -> SmsSubmissionClient)? = null

    fun capture(api: ApiClient): SmsSubmissionClient = factoryOverride?.invoke(api) ?: BoundApi(api)

    private class BoundApi(private val api: ApiClient) : SmsSubmissionClient {
        override fun createMessage(envelope: RetryEnvelope) = api.createMessage(envelope)
        override fun getMessage(messageId: String) = api.getMessage(messageId)
    }
}
