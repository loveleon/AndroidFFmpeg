package com.wlanjie.streaming.video;

import android.content.res.Resources;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;

import com.wlanjie.streaming.R;
import com.wlanjie.streaming.util.OpenGLUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Created by wlanjie on 2016/12/12.
 */

public final class Effect {

  private int mInputWidth;
  private int mInputHeight;
  private int mVideoWidth;
  private int mVideoHeight;

  private int mFboId = -1;
  private int mFboTextureId = -1;
  private IntBuffer mFboBuffer;

  private final FloatBuffer mCubeBuffer;
  private final FloatBuffer mTextureBuffer;
  private final FloatBuffer mReadPixelTextureBuffer;

  private int mProgramId;
  private int mPosition;
  private int mUniformTexture;
  private int mTextureCoordinate;
  private int mTextureTransform;
  private float[] mTextureTransformMatrix;
  private final float[] TEXTURE_BUFFER = {
      1.0f, 0.0f,
      1.0f, 1.0f,
      0.0f, 0.0f,
      0.0f, 1.0f
  };

  private final Resources mResources;

  Effect(Resources resources) {
    this.mResources = resources;
    mCubeBuffer = ByteBuffer.allocateDirect(OpenGLUtils.CUBE.length * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer();
    mCubeBuffer.put(OpenGLUtils.CUBE).position(0);

    mTextureBuffer = ByteBuffer.allocateDirect(OpenGLUtils.TEXTURE.length * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer();
    mTextureBuffer.put(OpenGLUtils.TEXTURE).position(0);

    mReadPixelTextureBuffer = ByteBuffer.allocateDirect(TEXTURE_BUFFER.length * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer();
    mReadPixelTextureBuffer.put(TEXTURE_BUFFER).position(0);
  }

  public void init() {
    mProgramId = OpenGLUtils.loadProgram(OpenGLUtils.readSharedFromRawResource(mResources, R.raw.vertex_oes), OpenGLUtils.readSharedFromRawResource(mResources, R.raw.fragment_oes));
    mPosition = GLES20.glGetAttribLocation(mProgramId, "position");
    mUniformTexture = GLES20.glGetUniformLocation(mProgramId, "inputImageTexture");
    mTextureCoordinate = GLES20.glGetAttribLocation(mProgramId, "inputTextureCoordinate");
    mTextureTransform = GLES20.glGetUniformLocation(mProgramId, "textureTransform");
  }

  void onInputSizeChanged(int width, int height) {
    this.mInputWidth = width;
    this.mInputHeight = height;
//    initFboTexture(mInputWidth, mInputHeight);
  }

  void setVideoSize(int width, int height) {
    mVideoWidth = width;
    mVideoHeight = height;
  }

  private void initFboTexture(int width, int height) {
    if (mFboId != 0 && width != mInputWidth && height != mInputHeight) {
      destroyFboTexture();
    }
    mFboBuffer = IntBuffer.allocate(width * height);
    int[] fbo = new int[1];
    int[] texture = new int[1];

    GLES20.glGenFramebuffers(1, fbo, 0);
    GLES20.glGenTextures(1, texture, 0);

    mFboId = fbo[0];
    mFboTextureId = texture[0];

    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mFboTextureId);
    GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
    GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
    GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
    GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
    GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFboId);
    GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, mFboTextureId, 0);
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
  }

  private void destroyFboTexture() {
    if (mFboId != 0) {
      GLES20.glDeleteFramebuffers(1, new int[] { mFboId }, 0);
    }
    if (mFboTextureId != 0) {
      GLES20.glDeleteTextures(1, new int[] { mFboTextureId }, 0);
    }
  }

  IntBuffer getRgbaBuffer() {
    return mFboBuffer;
  }

  int drawToFboTexture(int textureId, FloatBuffer cubeBuffer, FloatBuffer textureBuffer) {
    readPixel(textureId);

    GLES20.glUseProgram(mProgramId);
    GLES20.glEnableVertexAttribArray(mPosition);
    GLES20.glVertexAttribPointer(mPosition, 2, GLES20.GL_FLOAT, false, 4 * 2, cubeBuffer);

    GLES20.glEnableVertexAttribArray(mTextureCoordinate);
    GLES20.glVertexAttribPointer(mTextureCoordinate, 2, GLES20.GL_FLOAT, false, 4 * 2, textureBuffer);

    GLES20.glUniformMatrix4fv(mTextureTransform, 1, false, mTextureTransformMatrix, 0);

    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
    GLES20.glUniform1i(mUniformTexture, 0);

    GLES20.glViewport(0, 0, mInputWidth, mInputHeight);
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFboId);
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);

    GLES20.glDisableVertexAttribArray(mPosition);
    GLES20.glDisableVertexAttribArray(mTextureCoordinate);
    return mFboTextureId;
  }

  void readPixel(int textureId) {
//    GLES20.glUseProgram(mProgramId);
//
//    GLES20.glEnableVertexAttribArray(mPosition);
//    GLES20.glVertexAttribPointer(mPosition, 2, GLES20.GL_FLOAT, false, 4 * 2, mCubeBuffer);
//
//    GLES20.glEnableVertexAttribArray(mTextureCoordinate);
//    GLES20.glVertexAttribPointer(mTextureCoordinate, 2, GLES20.GL_FLOAT, false, 4 * 2, mReadPixelTextureBuffer);
//
//    GLES20.glUniformMatrix4fv(mTextureTransform, 1, false, mTextureTransformMatrix, 0);

    if (mFboId == -1) {
      int[] fbo = new int[1];
      mFboId = fbo[0];

      mFboBuffer = IntBuffer.allocate(mInputWidth * mInputHeight);
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
      GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, mInputWidth, mInputHeight, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
      GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
      GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
      GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
      GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
      GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFboId);
      GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, textureId, 0);
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
      GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    }
    GLES20.glViewport(0, 0, mInputWidth, mInputHeight);
    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
    GLES20.glUniform1i(mUniformTexture, 0);

    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFboId);
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    if (mVideoWidth > 0 && mVideoHeight > 0) {
      GLES20.glReadPixels(0, 0, mInputWidth, mInputHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, mFboBuffer);
    }
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

    GLES20.glDisableVertexAttribArray(mPosition);
    GLES20.glDisableVertexAttribArray(mTextureCoordinate);
  }

  int drawToFboTexture(int textureId) {
    return drawToFboTexture(textureId, mCubeBuffer, mTextureBuffer);
  }

  void setTextureTransformMatrix(float[] matrix) {
    mTextureTransformMatrix = matrix;
  }

  public final void destroy() {
    destroyFboTexture();
    GLES20.glDeleteProgram(mProgramId);
  }
}
