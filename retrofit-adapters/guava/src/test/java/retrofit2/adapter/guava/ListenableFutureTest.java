/*
 * Copyright (C) 2016 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package retrofit2.adapter.guava;

import static okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AFTER_REQUEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

import com.google.common.util.concurrent.ListenableFuture;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

import com.google.gson.Gson;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;

public final class ListenableFutureTest {
  @Rule public final MockWebServer server = new MockWebServer();

  interface Service {
    @GET("/")
    ListenableFuture<String> body();
    @GET("/")
    ListenableFuture<NodeToken> body2();

    @GET("/")
    Call<NodeToken> body3();

    @GET("/")
    ListenableFuture<Response<String>> response();
  }

  private Service service;

  @Before
  public void setUp() {
    Retrofit retrofit =
        new Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .addConverterFactory(new StringConverterFactory())
            .addCallAdapterFactory(GuavaCallAdapterFactory.create())
            .build();
    service = retrofit.create(Service.class);
  }

  protected static class NodeToken {
    private String access_token;
    private String access_token_expire;

    public String getAccess_token() {
      return access_token;
    }

    public void setAccess_token(String access_token) {
      this.access_token = access_token;
    }

    public String getAccess_token_expire() {
      return access_token_expire;
    }

    public void setAccess_token_expire(String access_token_expire) {
      this.access_token_expire = access_token_expire;
    }

    @Override
    public String toString() {
      return "NodeToken{" +
              "access_token='" + access_token + '\'' +
              ", access_token_expire='" + access_token_expire + '\'' +
              '}';
    }
  }

  @Test
  public void bodySuccess200() throws Exception {
    server.enqueue(new MockResponse().setBody("Hi"));

    ListenableFuture<String> future = service.body();
    assertThat(future.get()).isEqualTo("Hi");
  }

  @Test
  public void bodySuccess200_2() throws Exception {
    Gson gson = new Gson();
    String json = "{\n" +
            "    \"access_token\": \"tokentokentoken\",\n" +
            "    \"access_token_expire\": 3599\n" +
            "}";
    NodeToken tokenBean = gson.fromJson(json, NodeToken.class);
    System.out.println(tokenBean.access_token);

    server.enqueue(new MockResponse().setBody(json));
    ListenableFuture<NodeToken> future = service.body2();
    NodeToken body2 = future.get();
    System.out.println("bodySuccess200: body2: " + body2);
    assertThat(body2.access_token).isEqualTo(tokenBean.access_token);

    server.enqueue(new MockResponse().setBody(json));
    NodeToken body3 = service.body3().execute().body();
    System.out.println("bodySuccess200: body3: " + body3);
    assertThat(body3.access_token).isEqualTo(tokenBean.access_token);

  }

  @Test
  public void bodySuccess404() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(404));

    ListenableFuture<String> future = service.body();
    try {
      future.get();
      fail();
    } catch (ExecutionException e) {
      assertThat(e.getCause())
          .isInstanceOf(HttpException.class) // Required for backwards compatibility.
          .isInstanceOf(retrofit2.HttpException.class)
          .hasMessage("HTTP 404 Client Error");
    }
  }

  @Test
  public void bodyFailure() throws Exception {
    server.enqueue(new MockResponse().setSocketPolicy(DISCONNECT_AFTER_REQUEST));

    ListenableFuture<String> future = service.body();
    try {
      future.get();
      fail();
    } catch (ExecutionException e) {
      assertThat(e.getCause()).isInstanceOf(IOException.class);
    }
  }

  @Test
  public void responseSuccess200() throws Exception {
    server.enqueue(new MockResponse().setBody("Hi"));

    ListenableFuture<Response<String>> future = service.response();
    Response<String> response = future.get();
    assertThat(response.isSuccessful()).isTrue();
    assertThat(response.body()).isEqualTo("Hi");
  }

  @Test
  public void responseSuccess404() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(404).setBody("Hi"));

    ListenableFuture<Response<String>> future = service.response();
    Response<String> response = future.get();
    assertThat(response.isSuccessful()).isFalse();
    assertThat(response.errorBody().string()).isEqualTo("Hi");
  }

  @Test
  public void responseFailure() throws Exception {
    server.enqueue(new MockResponse().setSocketPolicy(DISCONNECT_AFTER_REQUEST));

    ListenableFuture<Response<String>> future = service.response();
    try {
      future.get();
      fail();
    } catch (ExecutionException e) {
      assertThat(e.getCause()).isInstanceOf(IOException.class);
    }
  }
}
