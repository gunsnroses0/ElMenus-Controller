import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpObjectDecoder;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder.EndOfDataDecoderException;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.util.CharsetUtil;
import io.netty.util.IllegalReferenceCountException;
import io.netty.handler.codec.http.multipart.Attribute;

public class ControllerHandler extends SimpleChannelInboundHandler<Object> {
	private HttpRequest request;
	private HttpPostRequestDecoder httpDecoder;
	private static final HttpDataFactory factory = new DefaultHttpDataFactory(true);
	private static final String FILE_UPLOAD_LOCN = "/Users/hazemsalah/Desktop/uploads/";
	
    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        System.out.println(ctx.handler() + " added");
        Controller.channels.add(ctx);
        super.handlerAdded(ctx);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        Controller.channels.remove(ctx);
        System.out.println(ctx.handler() + " removed");
        super.handlerRemoved(ctx);
    }


    @Override
    public void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
    	FullHttpRequest req = (FullHttpRequest) msg;
    	System.out.println("REQUEST::"+req);
    	String formString = "";
    	JSONParser formParser = new JSONParser();
		JSONObject formJson = new JSONObject();
		if (req.method().toString() == "POST" || req.method().toString() == "PATCH") {
			httpDecoder = new HttpPostRequestDecoder(factory, (HttpRequest) msg);
			httpDecoder.setDiscardThreshold(0);
			if (httpDecoder != null) {
				if (msg instanceof HttpContent) {
					HttpContent chunk = (HttpContent) msg;
					System.out.println("CHUNK"+chunk);
					httpDecoder.offer(chunk);
					formString = readChunk(ctx);
					System.out.println("FORM"+ formString);
					formJson = (JSONObject) formParser.parse(formString);
					if (chunk instanceof LastHttpContent) {
						resetPostRequestDecoder();
					}
				}
			}
		}
    }
	private String readChunk(ChannelHandlerContext ctx) throws IOException, InterruptedException {
		JSONObject json = new JSONObject();
		try {
			while (httpDecoder.hasNext()) {
				InterfaceHttpData data = httpDecoder.next();
				System.out.println(data.getHttpDataType());
				if (data != null) {
					try {
						switch (data.getHttpDataType()) {
						case FileUpload:
							String fileName = "";
							FileUpload fileUpload = (FileUpload) data;
							
							fileName = fileUpload.getFilename();
							int i = 1;
							
							String removedExtension = fileName.substring(0, fileName.length() - 5);
							String[] splitted = removedExtension.split("(?=\\p{Lu})");
							File file = null;
							switch (splitted[1]) {
							case "Like":
								file = new File(FILE_UPLOAD_LOCN+"/Likes/" + fileUpload.getFilename());
								break;
							case "Follower":
								file = new File(FILE_UPLOAD_LOCN+"/Followers/" + fileUpload.getFilename());
								break;
								
							}
//							while (file.exists()) {
//								String fileArray[] = fileUpload.getFilename().split("\\.");
//								
//								fileName = "";
//								for (int j = 0; j < fileArray.length - 1; j++) {
//									fileName += fileArray[j] + ".";
//								}
//								
//								fileName = fileName.substring(0, fileName.length() - 1);
//								fileName += " (" + i + ")." + fileArray[fileArray.length - 1];
//								System.out.println("NAME"+ fileName);
//								file = new File(FILE_UPLOAD_LOCN + fileName);
//								i++;
//							}
							file.createNewFile();
							try (FileChannel inputChannel = new FileInputStream(fileUpload.getFile()).getChannel();
									FileChannel outputChannel = new FileOutputStream(file).getChannel()) {
								outputChannel.transferFrom(inputChannel, 0, inputChannel.size());
							}
							json.put("media", fileName);
							break;
						}
					} finally {
						data.release();
					}
				}
			}
		} catch (EndOfDataDecoderException e) {
		} finally {
			return json.toString();
		}
	}



    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
		ctx.flush();
		ctx.fireChannelReadComplete();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }
	private void resetPostRequestDecoder() {
		try {
			request = null;
			httpDecoder.destroy();
			httpDecoder = null;
		} catch (IllegalReferenceCountException e) {

		}
	}
   
}
