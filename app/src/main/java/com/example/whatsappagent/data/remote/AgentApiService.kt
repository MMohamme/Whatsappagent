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
    
    @PATCH("messages/{msg_id}")
    suspend fun updateMessageStatus(
        @Path("msg_id") msgId: String,
        @Query("status") status: String
    ): Response<Map<String, String>>
    
    @GET("health")
    suspend fun healthCheck(): Response<Map<String, String>>
    
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
    @GET("contacts/{name}/events")
    suspend fun getContactEvents(@Path("name") name: String): Response<List<EventResponse>>

    @GET("events")
    suspend fun getAllEvents(@Query("status") status: String? = null): Response<List<EventResponse>>

    @POST("contacts/{name}/events")
    suspend fun createEvent(@Path("name") name: String, @Body event: EventCreate): Response<EventResponse>

    @PATCH("events/{id}")
    suspend fun updateEvent(@Path("id") id: Int, @Body update: EventUpdate): Response<EventResponse>

    @DELETE("events/{id}")
    suspend fun deleteEvent(@Path("id") id: Int): Response<Map<String, String>>

    @POST("events/{id}/trigger")
    suspend fun triggerEvent(@Path("id") id: Int): Response<EventTriggerResponse>
}
