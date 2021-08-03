package retrofit2;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import javax.annotation.Nullable;
import kotlin.Unit;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.http.Streaming;

final class BuiltInConverters extends Converter.Factory {
  private boolean checkForKotlinUnit = true;
  
  @Nullable
  public Converter<ResponseBody, ?> responseBodyConverter(Type type, Annotation[] annotations, Retrofit retrofit) {
    if (type == ResponseBody.class)
      return Utils.isAnnotationPresent(annotations, (Class)Streaming.class) ? 
        StreamingResponseBodyConverter.INSTANCE : 
        BufferingResponseBodyConverter.INSTANCE; 
    if (type == Void.class)
      return VoidResponseBodyConverter.INSTANCE; 
    if (this.checkForKotlinUnit)
      try {
        if (type == Unit.class)
          return UnitResponseBodyConverter.INSTANCE; 
      } catch (NoClassDefFoundError ignored) {
        this.checkForKotlinUnit = false;
      }  
    return null;
  }
  
  @Nullable
  public Converter<?, RequestBody> requestBodyConverter(Type type, Annotation[] parameterAnnotations, Annotation[] methodAnnotations, Retrofit retrofit) {
    if (RequestBody.class.isAssignableFrom(Utils.getRawType(type)))
      return RequestBodyConverter.INSTANCE; 
    return null;
  }
  
  static final class VoidResponseBodyConverter implements Converter<ResponseBody, Void> {
    static final VoidResponseBodyConverter INSTANCE = new VoidResponseBodyConverter();
    
    public Void convert(ResponseBody value) {
      value.close();
      return null;
    }
  }
  
  static final class UnitResponseBodyConverter implements Converter<ResponseBody, Unit> {
    static final UnitResponseBodyConverter INSTANCE = new UnitResponseBodyConverter();
    
    public Unit convert(ResponseBody value) {
      value.close();
      return Unit.INSTANCE;
    }
  }
  
  static final class RequestBodyConverter implements Converter<RequestBody, RequestBody> {
    static final RequestBodyConverter INSTANCE = new RequestBodyConverter();
    
    public RequestBody convert(RequestBody value) {
      return value;
    }
  }
  
  static final class StreamingResponseBodyConverter implements Converter<ResponseBody, ResponseBody> {
    static final StreamingResponseBodyConverter INSTANCE = new StreamingResponseBodyConverter();
    
    public ResponseBody convert(ResponseBody value) {
      return value;
    }
  }
  
  static final class BufferingResponseBodyConverter implements Converter<ResponseBody, ResponseBody> {
    static final BufferingResponseBodyConverter INSTANCE = new BufferingResponseBodyConverter();
    
    public ResponseBody convert(ResponseBody value) throws IOException {
      try {
        return Utils.buffer(value);
      } finally {
        value.close();
      } 
    }
  }
  
  static final class ToStringConverter implements Converter<Object, String> {
    static final ToStringConverter INSTANCE = new ToStringConverter();
    
    public String convert(Object value) {
      return value.toString();
    }
  }
}
