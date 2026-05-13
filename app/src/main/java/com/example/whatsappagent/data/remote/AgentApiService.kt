package com.example.whatsappagent.data.remote

import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit API service for backend communication
 * Defines all endpoints for the WhatsApp Agent backend
 */
interface AgentApiService {
    
    // --- Stats ---
    @GET("stats")
    suspend fun getStats(): Response<StatsResponse>
    
    // --- Queue ---
    @GET("queue")
    suspend fun getQueue(
        @Query("status") status: String? = null,
        @Query("limit") limit: Int = 50
    ): Response<List<QueueMessageResponse>>
    
    // --- Message Generation ---
    @POST("generate")
    suspend fun generateMessage(@Body message: MessageSchema): Response<Map<String, String>>

    @POST("messages/inbound")
    suspend fun inboundMessage(@Body message: InboundMessageRequest): Response<AgentDecisionResponse>
    
    @PATCH("messages/{msg_id}")
    suspend fun updateMessageStatus(
        @Path("msg_id") msgId: String,
        @Query("status") status: String
    ): Response<Map<String, String>>
    
    @GET("health")
    suspend fun healthCheck(): Response<Map<String, String>>

    // --- v3 Notes ---
    @GET("notes")
    suspend fun getScopedNotes(
        @Query("scope") scope: String? = null,
        @Query("contact_id") contactId: Long? = null,
        @Query("category") category: String? = null,
        @Query("active_only") activeOnly: Boolean = false
    ): Response<List<NoteResponse>>

    @POST("notes")
    suspend fun createScopedNote(@Body note: NoteRequest): Response<NoteResponse>

    @PATCH("notes/{id}")
    suspend fun updateScopedNote(@Path("id") id: Long, @Body note: NoteRequest): Response<NoteResponse>

    @DELETE("notes/{id}")
    suspend fun deleteScopedNote(@Path("id") id: Long): Response<Map<String, String>>

    // --- v3 Event tickets ---
    @GET("event-tickets")
    suspend fun getEventTickets(@Query("status") status: String? = null): Response<List<EventTicketResponse>>

    @POST("event-tickets")
    suspend fun createEventTicket(@Body event: EventTicketRequest): Response<EventTicketResponse>

    @GET("event-tickets/{id}")
    suspend fun getEventTicket(@Path("id") id: Long): Response<EventTicketResponse>

    @POST("event-tickets/{id}/prepare")
    suspend fun prepareEventTicket(@Path("id") id: Long): Response<EventTicketResponse>

    @POST("event-tickets/{id}/approve")
    suspend fun approveEventTicket(@Path("id") id: Long): Response<EventTicketResponse>

    @POST("event-tickets/{id}/cancel")
    suspend fun cancelEventTicket(@Path("id") id: Long): Response<EventTicketResponse>

    @GET("event-recipients/due")
    suspend fun getDueEventRecipients(): Response<List<EventRecipientResponse>>

    @POST("drafts/{id}/send-attempts")
    suspend fun createSendAttempt(@Path("id") draftId: Long, @Body request: SendAttemptRequest): Response<SendAttemptResponse>

    @PATCH("send-attempts/{id}")
    suspend fun updateSendAttempt(@Path("id") attemptId: Long, @Body update: SendAttemptUpdate): Response<SendAttemptResponse>

    @PATCH("drafts/{id}/decision")
    suspend fun updateDraftDecision(@Path("id") draftId: Long, @Body update: DraftDecisionUpdate): Response<QueueMessageResponse>
    
    // --- Contacts ---
    @GET("contacts")
    suspend fun getContacts(): Response<List<ContactResponse>>
    
    @POST("contacts")
    suspend fun createContact(@Body contact: ContactCreate): Response<ContactResponse>
    
    @PATCH("contacts/{contact_name}")
    suspend fun updateContact(
        @Path("contact_name") contactName: String,
        @Body update: ContactUpdate,
    ): Response<ContactResponse>
    
    @DELETE("contacts/{contact_name}")
    suspend fun deleteContact(@Path("contact_name") contactName: String): Response<Map<String, String>>
    
    @GET("contacts/{contact_name}/history")
    suspend fun getContactHistory(
        @Path("contact_name") contactName: String,
        @Query("limit") limit: Int = 30
    ): Response<List<MessageHistory>>

    // --- Notes ---
    @GET("contacts/{name}/notes")
    suspend fun getNotes(@Path("name") name: String): Response<List<NoteResponse>>

    @POST("contacts/{name}/notes")
    suspend fun createNote(@Path("name") name: String, @Body note: NoteCreate): Response<NoteResponse>

    @PATCH("contacts/{name}/notes/{id}")
    suspend fun updateNote(
        @Path("name") name: String,
        @Path("id") id: Int,
        @Body update: NoteUpdate
    ): Response<NoteResponse>

    @DELETE("contacts/{name}/notes/{id}")
    suspend fun deleteNote(@Path("name") name: String, @Path("id") id: Int): Response<Map<String, String>>

    // --- Events ---
    @GET("events")
    suspend fun getAllEvents(@Query("status") status: String? = null): Response<List<EventResponse>>

    @PATCH("events/{id}")
    suspend fun updateEvent(@Path("id") id: Int, @Body update: EventUpdate): Response<EventResponse>
}
