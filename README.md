[![Build Status](https://travis-ci.org/wenhuiyao/PrioritizeRetrofit.svg?branch=master)](https://travis-ci.org/wenhuiyao/PrioritizeRetrofit)

##### Adapt a [Retrofit](http://square.github.io/retrofit/) async call to support request priority

##### Example:

```java
    private void initRetrofit() {
        Retrofit.Builder()
               .baseUrl(BASE_URL)
               .addCallAdapterFactory(PrioritizedCallAdapterFactory.create()) // <-- add calladapter factory
               .addConverterFactory(MoshiConverterFactory.create())
               .build();
    }
  
    public interface ExampleService {
        @Priority(Priorities.HIGH) // <-- add priority annotation, that's it
        @GET("/")
        Call<String> getExamples();
    }
    
```

If the return type is not `Call`, use `PrioritizedCallFactory` to create a new Call instance that 
supports request priority

##### Gradle

```
    compile 'com.wenhui:prioritizeretrofitadapter:1.0.1'

```


##### *NOTE: only asynchonou call will be prioritized, synchronous call will not. Recommend benchmarking your service before using this since OkHttp client is optimize for multiple requests.*





