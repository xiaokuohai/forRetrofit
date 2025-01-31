package retrofit2;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import kotlin.coroutines.Continuation;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import retrofit2.http.DELETE;
import retrofit2.http.Field;
import retrofit2.http.FieldMap;
import retrofit2.http.GET;
import retrofit2.http.HEAD;
import retrofit2.http.HTTP;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.OPTIONS;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Part;
import retrofit2.http.PartMap;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.QueryMap;
import retrofit2.http.QueryName;

final class RequestFactory {
  private final Method method;
  
  private final HttpUrl baseUrl;
  
  final String httpMethod;
  
  @Nullable
  private final String relativeUrl;
  
  @Nullable
  private final Headers headers;
  
  @Nullable
  private final MediaType contentType;
  
  private final boolean hasBody;
  
  private final boolean isFormEncoded;
  
  private final boolean isMultipart;
  
  private final ParameterHandler<?>[] parameterHandlers;
  
  final boolean isKotlinSuspendFunction;
  
  static RequestFactory parseAnnotations(Retrofit retrofit, Method method) {
    return (new Builder(retrofit, method)).build();
  }
  
  RequestFactory(Builder builder) {
    this.method = builder.method;
    this.baseUrl = builder.retrofit.baseUrl;
    this.httpMethod = builder.httpMethod;
    this.relativeUrl = builder.relativeUrl;
    this.headers = builder.headers;
    this.contentType = builder.contentType;
    this.hasBody = builder.hasBody;
    this.isFormEncoded = builder.isFormEncoded;
    this.isMultipart = builder.isMultipart;
    this.parameterHandlers = builder.parameterHandlers;
    this.isKotlinSuspendFunction = builder.isKotlinSuspendFunction;
  }
  
  Request create(Object[] args) throws IOException {
    ParameterHandler<?>[] arrayOfParameterHandler = this.parameterHandlers;
    int argumentCount = args.length;
    if (argumentCount != arrayOfParameterHandler.length)
      throw new IllegalArgumentException("Argument count (" + argumentCount + ") doesn't match expected count (" + arrayOfParameterHandler.length + ")");
    //这里这里在构建requestBuilder
    RequestBuilder requestBuilder = new RequestBuilder(this.httpMethod, this.baseUrl, this.relativeUrl, this.headers, this.contentType, this.hasBody, this.isFormEncoded, this.isMultipart);
    if (this.isKotlinSuspendFunction)
      argumentCount--; 
    List<Object> argumentList = new ArrayList(argumentCount);
    for (int p = 0; p < argumentCount; p++) {
      argumentList.add(args[p]);
      arrayOfParameterHandler[p].apply(requestBuilder, args[p]);
    } 
    return requestBuilder.get().tag(Invocation.class, new Invocation(this.method, argumentList)).build();
  }
  
  static final class Builder {
    private static final String PARAM = "[a-zA-Z][a-zA-Z0-9_-]*";
    
    private static final Pattern PARAM_URL_REGEX = Pattern.compile("\\{([a-zA-Z][a-zA-Z0-9_-]*)\\}");
    
    private static final Pattern PARAM_NAME_REGEX = Pattern.compile("[a-zA-Z][a-zA-Z0-9_-]*");
    
    final Retrofit retrofit;
    
    final Method method;
    
    final Annotation[] methodAnnotations;
    
    final Annotation[][] parameterAnnotationsArray;
    
    final Type[] parameterTypes;
    
    boolean gotField;
    
    boolean gotPart;
    
    boolean gotBody;
    
    boolean gotPath;
    
    boolean gotQuery;
    
    boolean gotQueryName;
    
    boolean gotQueryMap;
    
    boolean gotUrl;
    
    @Nullable
    String httpMethod;
    
    boolean hasBody;
    
    boolean isFormEncoded;
    
    boolean isMultipart;
    
    @Nullable
    String relativeUrl;
    
    @Nullable
    Headers headers;
    
    @Nullable
    MediaType contentType;
    
    @Nullable
    Set<String> relativeUrlParamNames;
    
    @Nullable
    ParameterHandler<?>[] parameterHandlers;
    
    boolean isKotlinSuspendFunction;
    
    Builder(Retrofit retrofit, Method method) {
      this.retrofit = retrofit;
      this.method = method;
      this.methodAnnotations = method.getAnnotations();//获取所有的注解，包括自己声明的以及继承的
      this.parameterTypes = method.getGenericParameterTypes(); //获取到方法的参数列表
      this.parameterAnnotationsArray = method.getParameterAnnotations();//得到参数的注解列表
    }
    
    RequestFactory build() {
      for (Annotation annotation : this.methodAnnotations)
        parseMethodAnnotation(annotation);
      if (this.httpMethod == null)
        throw Utils.methodError(this.method, "HTTP method annotation is required (e.g., @GET, @POST, etc.).", new Object[0]); 
      if (!this.hasBody) {
        if (this.isMultipart)
          throw Utils.methodError(this.method, "Multipart can only be specified on HTTP methods with request body (e.g., @POST).", new Object[0]); 
        if (this.isFormEncoded)
          throw Utils.methodError(this.method, "FormUrlEncoded can only be specified on HTTP methods with request body (e.g., @POST).", new Object[0]); 
      } 
      int parameterCount = this.parameterAnnotationsArray.length;
      this.parameterHandlers = (ParameterHandler<?>[])new ParameterHandler[parameterCount];
      for (int p = 0, lastParameter = parameterCount - 1; p < parameterCount; p++)
        this.parameterHandlers[p] = 
          parseParameter(p, this.parameterTypes[p], this.parameterAnnotationsArray[p], (p == lastParameter)); 
      if (this.relativeUrl == null && !this.gotUrl)
        throw Utils.methodError(this.method, "Missing either @%s URL or @Url parameter.", new Object[] { this.httpMethod }); 
      if (!this.isFormEncoded && !this.isMultipart && !this.hasBody && this.gotBody)
        throw Utils.methodError(this.method, "Non-body HTTP method cannot contain @Body.", new Object[0]); 
      if (this.isFormEncoded && !this.gotField)
        throw Utils.methodError(this.method, "Form-encoded method must contain at least one @Field.", new Object[0]); 
      if (this.isMultipart && !this.gotPart)
        throw Utils.methodError(this.method, "Multipart method must contain at least one @Part.", new Object[0]); 
      return new RequestFactory(this);
    }
    
    private void parseMethodAnnotation(Annotation annotation) {  //对注解头的分析
      if (annotation instanceof DELETE) {
        parseHttpMethodAndPath("DELETE", ((DELETE)annotation).value(), false);
      } else if (annotation instanceof GET) {
        parseHttpMethodAndPath("GET", ((GET)annotation).value(), false);
      } else if (annotation instanceof HEAD) {
        parseHttpMethodAndPath("HEAD", ((HEAD)annotation).value(), false);
      } else if (annotation instanceof PATCH) {
        parseHttpMethodAndPath("PATCH", ((PATCH)annotation).value(), true);
      } else if (annotation instanceof POST) {
        parseHttpMethodAndPath("POST", ((POST)annotation).value(), true);
      } else if (annotation instanceof PUT) {
        parseHttpMethodAndPath("PUT", ((PUT)annotation).value(), true);
      } else if (annotation instanceof OPTIONS) {
        parseHttpMethodAndPath("OPTIONS", ((OPTIONS)annotation).value(), false);
      } else if (annotation instanceof HTTP) {
        HTTP http = (HTTP)annotation;
        parseHttpMethodAndPath(http.method(), http.path(), http.hasBody());
      } else if (annotation instanceof Headers) {
        String[] headersToParse = ((Headers)annotation).value();
        if (headersToParse.length == 0)
          throw Utils.methodError(this.method, "@Headers annotation is empty.", new Object[0]); 
        this.headers = parseHeaders(headersToParse);
      } else if (annotation instanceof retrofit2.http.Multipart) {
        if (this.isFormEncoded)
          throw Utils.methodError(this.method, "Only one encoding annotation is allowed.", new Object[0]); 
        this.isMultipart = true;
      } else if (annotation instanceof retrofit2.http.FormUrlEncoded) {
        if (this.isMultipart)
          throw Utils.methodError(this.method, "Only one encoding annotation is allowed.", new Object[0]); 
        this.isFormEncoded = true;
      } 
    }
    
    private void parseHttpMethodAndPath(String httpMethod, String value, boolean hasBody) {
      if (this.httpMethod != null)
        throw Utils.methodError(this.method, "Only one HTTP method is allowed. Found: %s and %s.", new Object[] { this.httpMethod, httpMethod }); 
      this.httpMethod = httpMethod;
      this.hasBody = hasBody;
      if (value.isEmpty())
        return; 
      int question = value.indexOf('?');
      if (question != -1 && question < value.length() - 1) {
        String queryParams = value.substring(question + 1);
        Matcher queryParamMatcher = PARAM_URL_REGEX.matcher(queryParams);
        if (queryParamMatcher.find())
          throw Utils.methodError(this.method, "URL query string \"%s\" must not have replace block. For dynamic query parameters use @Query.", new Object[] { queryParams }); 
      } 
      this.relativeUrl = value;
      this.relativeUrlParamNames = parsePathParameters(value);
    }
    
    private Headers parseHeaders(String[] headers) {
      Headers.Builder builder = new Headers.Builder();
      for (String header : headers) {
        int colon = header.indexOf(':');
        if (colon == -1 || colon == 0 || colon == header.length() - 1)
          throw Utils.methodError(this.method, "@Headers value must be in the form \"Name: Value\". Found: \"%s\"", new Object[] { header }); 
        String headerName = header.substring(0, colon);
        String headerValue = header.substring(colon + 1).trim();
        if ("Content-Type".equalsIgnoreCase(headerName)) {
          try {
            this.contentType = MediaType.get(headerValue);
          } catch (IllegalArgumentException e) {
            throw Utils.methodError(this.method, e, "Malformed content type: %s", new Object[] { headerValue });
          } 
        } else {
          builder.add(headerName, headerValue);
        } 
      } 
      return builder.build();
    }
    
    @Nullable
    private ParameterHandler<?> parseParameter(int p, Type parameterType, @Nullable Annotation[] annotations, boolean allowContinuation) {
      ParameterHandler<?> result = null;
      if (annotations != null)
        for (Annotation annotation : annotations) {
          ParameterHandler<?> annotationAction = parseParameterAnnotation(p, parameterType, annotations, annotation);
          if (annotationAction != null) {
            if (result != null)
              throw Utils.parameterError(this.method, p, "Multiple Retrofit annotations found, only one allowed.", new Object[0]); 
            result = annotationAction;
          } 
        }  
      if (result == null) {
        if (allowContinuation)
          try {
            if (Utils.getRawType(parameterType) == Continuation.class) {
              this.isKotlinSuspendFunction = true;
              return null;
            } 
          } catch (NoClassDefFoundError noClassDefFoundError) {} 
        throw Utils.parameterError(this.method, p, "No Retrofit annotation found.", new Object[0]);
      } 
      return result;
    }
    
    @Nullable
    private ParameterHandler<?> parseParameterAnnotation(int p, Type type, Annotation[] annotations, Annotation annotation) {
      if (annotation instanceof retrofit2.http.Url) {
        validateResolvableType(p, type);
        if (this.gotUrl)
          throw Utils.parameterError(this.method, p, "Multiple @Url method annotations found.", new Object[0]); 
        if (this.gotPath)
          throw Utils.parameterError(this.method, p, "@Path parameters may not be used with @Url.", new Object[0]); 
        if (this.gotQuery)
          throw Utils.parameterError(this.method, p, "A @Url parameter must not come after a @Query.", new Object[0]); 
        if (this.gotQueryName)
          throw Utils.parameterError(this.method, p, "A @Url parameter must not come after a @QueryName.", new Object[0]); 
        if (this.gotQueryMap)
          throw Utils.parameterError(this.method, p, "A @Url parameter must not come after a @QueryMap.", new Object[0]); 
        if (this.relativeUrl != null)
          throw Utils.parameterError(this.method, p, "@Url cannot be used with @%s URL", new Object[] { this.httpMethod }); 
        this.gotUrl = true;
        if (type == HttpUrl.class || type == String.class || type == URI.class || (type instanceof Class && "android.net.Uri"
          
          .equals(((Class)type).getName())))
          return new ParameterHandler.RelativeUrl(this.method, p); 
        throw Utils.parameterError(this.method, p, "@Url must be okhttp3.HttpUrl, String, java.net.URI, or android.net.Uri type.", new Object[0]);
      } 
      if (annotation instanceof Path) {
        validateResolvableType(p, type);
        if (this.gotQuery)
          throw Utils.parameterError(this.method, p, "A @Path parameter must not come after a @Query.", new Object[0]); 
        if (this.gotQueryName)
          throw Utils.parameterError(this.method, p, "A @Path parameter must not come after a @QueryName.", new Object[0]); 
        if (this.gotQueryMap)
          throw Utils.parameterError(this.method, p, "A @Path parameter must not come after a @QueryMap.", new Object[0]); 
        if (this.gotUrl)
          throw Utils.parameterError(this.method, p, "@Path parameters may not be used with @Url.", new Object[0]); 
        if (this.relativeUrl == null)
          throw Utils.parameterError(this.method, p, "@Path can only be used with relative url on @%s", new Object[] { this.httpMethod }); 
        this.gotPath = true;
        Path path = (Path)annotation;
        String name = path.value();
        validatePathName(p, name);
        Converter<?, String> converter = this.retrofit.stringConverter(type, annotations);
        return new ParameterHandler.Path(this.method, p, name, converter, path.encoded());
      } 
      if (annotation instanceof Query) {
        validateResolvableType(p, type);
        Query query = (Query)annotation;
        String name = query.value();
        boolean encoded = query.encoded();
        Class<?> rawParameterType = Utils.getRawType(type);
        this.gotQuery = true;
        if (Iterable.class.isAssignableFrom(rawParameterType)) {
          if (!(type instanceof ParameterizedType))
            throw Utils.parameterError(this.method, p, rawParameterType
                
                .getSimpleName() + " must include generic type (e.g., " + rawParameterType
                
                .getSimpleName() + "<String>)", new Object[0]); 
          ParameterizedType parameterizedType = (ParameterizedType)type;
          Type iterableType = Utils.getParameterUpperBound(0, parameterizedType);
          Converter<?, String> converter1 = this.retrofit.stringConverter(iterableType, annotations);
          return (new ParameterHandler.Query(name, converter1, encoded)).iterable();
        } 
        if (rawParameterType.isArray()) {
          Class<?> arrayComponentType = boxIfPrimitive(rawParameterType.getComponentType());
          Converter<?, String> converter1 = this.retrofit.stringConverter(arrayComponentType, annotations);
          return (new ParameterHandler.Query(name, converter1, encoded)).array();
        } 
        Converter<?, String> converter = this.retrofit.stringConverter(type, annotations);
        return new ParameterHandler.Query(name, converter, encoded);
      } 
      if (annotation instanceof QueryName) {
        validateResolvableType(p, type);
        QueryName query = (QueryName)annotation;
        boolean encoded = query.encoded();
        Class<?> rawParameterType = Utils.getRawType(type);
        this.gotQueryName = true;
        if (Iterable.class.isAssignableFrom(rawParameterType)) {
          if (!(type instanceof ParameterizedType))
            throw Utils.parameterError(this.method, p, rawParameterType
                
                .getSimpleName() + " must include generic type (e.g., " + rawParameterType
                
                .getSimpleName() + "<String>)", new Object[0]); 
          ParameterizedType parameterizedType = (ParameterizedType)type;
          Type iterableType = Utils.getParameterUpperBound(0, parameterizedType);
          Converter<?, String> converter1 = this.retrofit.stringConverter(iterableType, annotations);
          return (new ParameterHandler.QueryName(converter1, encoded)).iterable();
        } 
        if (rawParameterType.isArray()) {
          Class<?> arrayComponentType = boxIfPrimitive(rawParameterType.getComponentType());
          Converter<?, String> converter1 = this.retrofit.stringConverter(arrayComponentType, annotations);
          return (new ParameterHandler.QueryName(converter1, encoded)).array();
        } 
        Converter<?, String> converter = this.retrofit.stringConverter(type, annotations);
        return new ParameterHandler.QueryName(converter, encoded);
      } 
      if (annotation instanceof QueryMap) {
        validateResolvableType(p, type);
        Class<?> rawParameterType = Utils.getRawType(type);
        this.gotQueryMap = true;
        if (!Map.class.isAssignableFrom(rawParameterType))
          throw Utils.parameterError(this.method, p, "@QueryMap parameter type must be Map.", new Object[0]); 
        Type mapType = Utils.getSupertype(type, rawParameterType, Map.class);
        if (!(mapType instanceof ParameterizedType))
          throw Utils.parameterError(this.method, p, "Map must include generic types (e.g., Map<String, String>)", new Object[0]); 
        ParameterizedType parameterizedType = (ParameterizedType)mapType;
        Type keyType = Utils.getParameterUpperBound(0, parameterizedType);
        if (String.class != keyType)
          throw Utils.parameterError(this.method, p, "@QueryMap keys must be of type String: " + keyType, new Object[0]); 
        Type valueType = Utils.getParameterUpperBound(1, parameterizedType);
        Converter<?, String> valueConverter = this.retrofit.stringConverter(valueType, annotations);
        return new ParameterHandler.QueryMap(this.method, p, valueConverter, ((QueryMap)annotation)
            .encoded());
      } 
      if (annotation instanceof Header) {
        validateResolvableType(p, type);
        Header header = (Header)annotation;
        String name = header.value();
        Class<?> rawParameterType = Utils.getRawType(type);
        if (Iterable.class.isAssignableFrom(rawParameterType)) {
          if (!(type instanceof ParameterizedType))
            throw Utils.parameterError(this.method, p, rawParameterType
                
                .getSimpleName() + " must include generic type (e.g., " + rawParameterType
                
                .getSimpleName() + "<String>)", new Object[0]); 
          ParameterizedType parameterizedType = (ParameterizedType)type;
          Type iterableType = Utils.getParameterUpperBound(0, parameterizedType);
          Converter<?, String> converter1 = this.retrofit.stringConverter(iterableType, annotations);
          return (new ParameterHandler.Header(name, converter1)).iterable();
        } 
        if (rawParameterType.isArray()) {
          Class<?> arrayComponentType = boxIfPrimitive(rawParameterType.getComponentType());
          Converter<?, String> converter1 = this.retrofit.stringConverter(arrayComponentType, annotations);
          return (new ParameterHandler.Header(name, converter1)).array();
        } 
        Converter<?, String> converter = this.retrofit.stringConverter(type, annotations);
        return new ParameterHandler.Header(name, converter);
      } 
      if (annotation instanceof retrofit2.http.HeaderMap) {
        if (type == Headers.class)
          return new ParameterHandler.Headers(this.method, p); 
        validateResolvableType(p, type);
        Class<?> rawParameterType = Utils.getRawType(type);
        if (!Map.class.isAssignableFrom(rawParameterType))
          throw Utils.parameterError(this.method, p, "@HeaderMap parameter type must be Map.", new Object[0]); 
        Type mapType = Utils.getSupertype(type, rawParameterType, Map.class);
        if (!(mapType instanceof ParameterizedType))
          throw Utils.parameterError(this.method, p, "Map must include generic types (e.g., Map<String, String>)", new Object[0]); 
        ParameterizedType parameterizedType = (ParameterizedType)mapType;
        Type keyType = Utils.getParameterUpperBound(0, parameterizedType);
        if (String.class != keyType)
          throw Utils.parameterError(this.method, p, "@HeaderMap keys must be of type String: " + keyType, new Object[0]); 
        Type valueType = Utils.getParameterUpperBound(1, parameterizedType);
        Converter<?, String> valueConverter = this.retrofit.stringConverter(valueType, annotations);
        return new ParameterHandler.HeaderMap(this.method, p, valueConverter);
      } 
      if (annotation instanceof Field) {
        validateResolvableType(p, type);
        if (!this.isFormEncoded)
          throw Utils.parameterError(this.method, p, "@Field parameters can only be used with form encoding.", new Object[0]); 
        Field field = (Field)annotation;
        String name = field.value();
        boolean encoded = field.encoded();
        this.gotField = true;
        Class<?> rawParameterType = Utils.getRawType(type);
        if (Iterable.class.isAssignableFrom(rawParameterType)) {
          if (!(type instanceof ParameterizedType))
            throw Utils.parameterError(this.method, p, rawParameterType
                
                .getSimpleName() + " must include generic type (e.g., " + rawParameterType
                
                .getSimpleName() + "<String>)", new Object[0]); 
          ParameterizedType parameterizedType = (ParameterizedType)type;
          Type iterableType = Utils.getParameterUpperBound(0, parameterizedType);
          Converter<?, String> converter1 = this.retrofit.stringConverter(iterableType, annotations);
          return (new ParameterHandler.Field(name, converter1, encoded)).iterable();
        } 
        if (rawParameterType.isArray()) {
          Class<?> arrayComponentType = boxIfPrimitive(rawParameterType.getComponentType());
          Converter<?, String> converter1 = this.retrofit.stringConverter(arrayComponentType, annotations);
          return (new ParameterHandler.Field(name, converter1, encoded)).array();
        } 
        Converter<?, String> converter = this.retrofit.stringConverter(type, annotations);
        return new ParameterHandler.Field(name, converter, encoded);
      } 
      if (annotation instanceof FieldMap) {
        validateResolvableType(p, type);
        if (!this.isFormEncoded)
          throw Utils.parameterError(this.method, p, "@FieldMap parameters can only be used with form encoding.", new Object[0]); 
        Class<?> rawParameterType = Utils.getRawType(type);
        if (!Map.class.isAssignableFrom(rawParameterType))
          throw Utils.parameterError(this.method, p, "@FieldMap parameter type must be Map.", new Object[0]); 
        Type mapType = Utils.getSupertype(type, rawParameterType, Map.class);
        if (!(mapType instanceof ParameterizedType))
          throw Utils.parameterError(this.method, p, "Map must include generic types (e.g., Map<String, String>)", new Object[0]); 
        ParameterizedType parameterizedType = (ParameterizedType)mapType;
        Type keyType = Utils.getParameterUpperBound(0, parameterizedType);
        if (String.class != keyType)
          throw Utils.parameterError(this.method, p, "@FieldMap keys must be of type String: " + keyType, new Object[0]); 
        Type valueType = Utils.getParameterUpperBound(1, parameterizedType);
        Converter<?, String> valueConverter = this.retrofit.stringConverter(valueType, annotations);
        this.gotField = true;
        return new ParameterHandler.FieldMap(this.method, p, valueConverter, ((FieldMap)annotation)
            .encoded());
      } 
      if (annotation instanceof Part) {
        validateResolvableType(p, type);
        if (!this.isMultipart)
          throw Utils.parameterError(this.method, p, "@Part parameters can only be used with multipart encoding.", new Object[0]); 
        Part part = (Part)annotation;
        this.gotPart = true;
        String partName = part.value();
        Class<?> rawParameterType = Utils.getRawType(type);
        if (partName.isEmpty()) {
          if (Iterable.class.isAssignableFrom(rawParameterType)) {
            if (!(type instanceof ParameterizedType))
              throw Utils.parameterError(this.method, p, rawParameterType
                  
                  .getSimpleName() + " must include generic type (e.g., " + rawParameterType
                  
                  .getSimpleName() + "<String>)", new Object[0]); 
            ParameterizedType parameterizedType = (ParameterizedType)type;
            Type iterableType = Utils.getParameterUpperBound(0, parameterizedType);
            if (!MultipartBody.Part.class.isAssignableFrom(Utils.getRawType(iterableType)))
              throw Utils.parameterError(this.method, p, "@Part annotation must supply a name or use MultipartBody.Part parameter type.", new Object[0]); 
            return ParameterHandler.RawPart.INSTANCE.iterable();
          } 
          if (rawParameterType.isArray()) {
            Class<?> arrayComponentType = rawParameterType.getComponentType();
            if (!MultipartBody.Part.class.isAssignableFrom(arrayComponentType))
              throw Utils.parameterError(this.method, p, "@Part annotation must supply a name or use MultipartBody.Part parameter type.", new Object[0]); 
            return ParameterHandler.RawPart.INSTANCE.array();
          } 
          if (MultipartBody.Part.class.isAssignableFrom(rawParameterType))
            return ParameterHandler.RawPart.INSTANCE; 
          throw Utils.parameterError(this.method, p, "@Part annotation must supply a name or use MultipartBody.Part parameter type.", new Object[0]);
        } 
        Headers headers = Headers.of(new String[] { "Content-Disposition", "form-data; name=\"" + partName + "\"", "Content-Transfer-Encoding", part.encoding() });
        if (Iterable.class.isAssignableFrom(rawParameterType)) {
          if (!(type instanceof ParameterizedType))
            throw Utils.parameterError(this.method, p, rawParameterType
                
                .getSimpleName() + " must include generic type (e.g., " + rawParameterType
                
                .getSimpleName() + "<String>)", new Object[0]); 
          ParameterizedType parameterizedType = (ParameterizedType)type;
          Type iterableType = Utils.getParameterUpperBound(0, parameterizedType);
          if (MultipartBody.Part.class.isAssignableFrom(Utils.getRawType(iterableType)))
            throw Utils.parameterError(this.method, p, "@Part parameters using the MultipartBody.Part must not include a part name in the annotation.", new Object[0]); 
          Converter<?, RequestBody> converter1 = this.retrofit.requestBodyConverter(iterableType, annotations, this.methodAnnotations);
          return (new ParameterHandler.Part(this.method, p, headers, converter1)).iterable();
        } 
        if (rawParameterType.isArray()) {
          Class<?> arrayComponentType = boxIfPrimitive(rawParameterType.getComponentType());
          if (MultipartBody.Part.class.isAssignableFrom(arrayComponentType))
            throw Utils.parameterError(this.method, p, "@Part parameters using the MultipartBody.Part must not include a part name in the annotation.", new Object[0]); 
          Converter<?, RequestBody> converter1 = this.retrofit.requestBodyConverter(arrayComponentType, annotations, this.methodAnnotations);
          return (new ParameterHandler.Part(this.method, p, headers, converter1)).array();
        } 
        if (MultipartBody.Part.class.isAssignableFrom(rawParameterType))
          throw Utils.parameterError(this.method, p, "@Part parameters using the MultipartBody.Part must not include a part name in the annotation.", new Object[0]); 
        Converter<?, RequestBody> converter = this.retrofit.requestBodyConverter(type, annotations, this.methodAnnotations);
        return new ParameterHandler.Part(this.method, p, headers, converter);
      } 
      if (annotation instanceof PartMap) {
        validateResolvableType(p, type);
        if (!this.isMultipart)
          throw Utils.parameterError(this.method, p, "@PartMap parameters can only be used with multipart encoding.", new Object[0]); 
        this.gotPart = true;
        Class<?> rawParameterType = Utils.getRawType(type);
        if (!Map.class.isAssignableFrom(rawParameterType))
          throw Utils.parameterError(this.method, p, "@PartMap parameter type must be Map.", new Object[0]); 
        Type mapType = Utils.getSupertype(type, rawParameterType, Map.class);
        if (!(mapType instanceof ParameterizedType))
          throw Utils.parameterError(this.method, p, "Map must include generic types (e.g., Map<String, String>)", new Object[0]); 
        ParameterizedType parameterizedType = (ParameterizedType)mapType;
        Type keyType = Utils.getParameterUpperBound(0, parameterizedType);
        if (String.class != keyType)
          throw Utils.parameterError(this.method, p, "@PartMap keys must be of type String: " + keyType, new Object[0]); 
        Type valueType = Utils.getParameterUpperBound(1, parameterizedType);
        if (MultipartBody.Part.class.isAssignableFrom(Utils.getRawType(valueType)))
          throw Utils.parameterError(this.method, p, "@PartMap values cannot be MultipartBody.Part. Use @Part List<Part> or a different value type instead.", new Object[0]); 
        Converter<?, RequestBody> valueConverter = this.retrofit.requestBodyConverter(valueType, annotations, this.methodAnnotations);
        PartMap partMap = (PartMap)annotation;
        return new ParameterHandler.PartMap(this.method, p, valueConverter, partMap.encoding());
      } 
      if (annotation instanceof retrofit2.http.Body) {
        Converter<?, RequestBody> converter;
        validateResolvableType(p, type);
        if (this.isFormEncoded || this.isMultipart)
          throw Utils.parameterError(this.method, p, "@Body parameters cannot be used with form or multi-part encoding.", new Object[0]); 
        if (this.gotBody)
          throw Utils.parameterError(this.method, p, "Multiple @Body method annotations found.", new Object[0]); 
        try {
          converter = this.retrofit.requestBodyConverter(type, annotations, this.methodAnnotations);
        } catch (RuntimeException e) {
          throw Utils.parameterError(this.method, e, p, "Unable to create @Body converter for %s", new Object[] { type });
        } 
        this.gotBody = true;
        return new ParameterHandler.Body(this.method, p, converter);
      } 
      if (annotation instanceof retrofit2.http.Tag) {
        validateResolvableType(p, type);
        Class<?> tagType = Utils.getRawType(type);
        for (int i = p - 1; i >= 0; i--) {
          ParameterHandler<?> otherHandler = this.parameterHandlers[i];
          if (otherHandler instanceof ParameterHandler.Tag && ((ParameterHandler.Tag)otherHandler).cls
            .equals(tagType))
            throw Utils.parameterError(this.method, p, "@Tag type " + tagType
                
                .getName() + " is duplicate of parameter #" + (i + 1) + " and would always overwrite its value.", new Object[0]); 
        } 
        return new ParameterHandler.Tag(tagType);
      } 
      return null;
    }
    
    private void validateResolvableType(int p, Type type) {
      if (Utils.hasUnresolvableType(type))
        throw Utils.parameterError(this.method, p, "Parameter type must not include a type variable or wildcard: %s", new Object[] { type }); 
    }
    
    private void validatePathName(int p, String name) {
      if (!PARAM_NAME_REGEX.matcher(name).matches())
        throw Utils.parameterError(this.method, p, "@Path parameter name must match %s. Found: %s", new Object[] { PARAM_URL_REGEX
              
              .pattern(), name }); 
      if (!this.relativeUrlParamNames.contains(name))
        throw Utils.parameterError(this.method, p, "URL \"%s\" does not contain \"{%s}\".", new Object[] { this.relativeUrl, name }); 
    }
    
    static Set<String> parsePathParameters(String path) {
      Matcher m = PARAM_URL_REGEX.matcher(path);
      Set<String> patterns = new LinkedHashSet<>();
      while (m.find())
        patterns.add(m.group(1)); 
      return patterns;
    }
    
    private static Class<?> boxIfPrimitive(Class<?> type) {
      if (boolean.class == type)
        return Boolean.class; 
      if (byte.class == type)
        return Byte.class; 
      if (char.class == type)
        return Character.class; 
      if (double.class == type)
        return Double.class; 
      if (float.class == type)
        return Float.class; 
      if (int.class == type)
        return Integer.class; 
      if (long.class == type)
        return Long.class; 
      if (short.class == type)
        return Short.class; 
      return type;
    }
  }
}
