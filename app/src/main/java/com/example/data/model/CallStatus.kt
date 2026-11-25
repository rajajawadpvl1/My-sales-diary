package com.example.data.model

/**
 * All 8 required call statuses with their follow-up scheduling semantics.
 */
enum class CallStatus(val label: String) {
    INTERESTED("Interested"),
    NOT_INTERESTED("Not Interested"),
    NOT_CONNECTED("Not Connected"),
    CALL_BACK("Call Back"),
    BUSY("Busy"),
    SWITCHED_OFF("Switched Off"),
    WRONG_NUMBER("Wrong Number"),
    ENROLLED("Enrolled");

    val isAutoTwoDayFollowUp: Boolean
        get() = this == INTERESTED

    val requiresCustomFollowUp: Boolean
        get() = this == CALL_BACK

    val supportsOptionalRetryFollowUp: Boolean
        get() = this == NOT_CONNECTED || this == BUSY

    val closesReminder: Boolean
        get() = this == NOT_INTERESTED || this == WRONG_NUMBER || this == ENROLLED

    companion object {
        fun fromStored(value: String?): CallStatus {
            if (value.isNullOrBlank()) return INTERESTED
            return entries.firstOrNull {
                it.name.equals(value.trim(), ignoreCase = true) ||
                    it.label.equals(value.trim(), ignoreCase = true)
            } ?: INTERESTED
        }
    }
}
