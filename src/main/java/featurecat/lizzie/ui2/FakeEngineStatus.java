package featurecat.lizzie.ui2;

import java.util.Objects;

/** Deterministic engine state for snapshots, smoke tests and UI review. */
public final class FakeEngineStatus implements EngineStatus {
  private final State state;
  private final String engineName;
  private final String message;

  public FakeEngineStatus(State state, String engineName, String message) {
    this.state = Objects.requireNonNull(state, "state");
    this.engineName = engineName == null ? "" : engineName;
    this.message = message == null ? "" : message;
  }

  public static FakeEngineStatus of(State state) {
    switch (state) {
      case NOT_LOADED:
        return new FakeEngineStatus(state, "", "未加载引擎");
      case LOADING:
        return new FakeEngineStatus(state, "", "正在加载引擎…");
      case ANALYZING:
        return new FakeEngineStatus(state, "v1.18.2 CUDA b11c768", "分析中");
      case PAUSED:
        return new FakeEngineStatus(state, "v1.18.2 CUDA b11c768", "已暂停");
      case ERROR:
        return new FakeEngineStatus(state, "", "引擎启动失败");
      default:
        throw new IllegalArgumentException("unknown state: " + state);
    }
  }

  @Override
  public State state() {
    return state;
  }

  @Override
  public String engineName() {
    return engineName;
  }

  @Override
  public String message() {
    return message;
  }

  @Override
  public String toString() {
    return "FakeEngineStatus{" + state + ", " + engineName + ", " + message + "}";
  }
}
