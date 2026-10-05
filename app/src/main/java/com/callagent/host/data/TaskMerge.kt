package com.callagent.host.data

data class AcceptedTaskState(
    val status: String,
    val partCount: Int?,
    val expiresAt: String?,
    val parts: List<SmsPart>,
    val from: String?,
    val to: String?
)

fun mergeAcceptedTaskState(
    requestStatus: String,
    accepted: MessageAccepted,
    observed: SmsRecord?,
    localRecipient: String?
): AcceptedTaskState = AcceptedTaskState(
    status = observed?.status ?: requestStatus,
    partCount = observed?.partCount ?: accepted.partCount,
    expiresAt = observed?.expiresAt ?: accepted.expiresAt,
    parts = observed?.parts ?: emptyList(),
    from = observed?.from,
    to = observed?.to ?: localRecipient
)
