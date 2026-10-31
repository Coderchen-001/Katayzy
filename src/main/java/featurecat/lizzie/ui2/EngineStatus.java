package featurecat.lizzie.ui2;

/** Read-only engine state consumed by the future UI2 shell. */
public interface EngineStatus {
  enum State {
    NOT_LOADED,
    LOADING,
    ANALYZING,
    PAUSED,
    ERROR
  }

  State state();

  String engineName();

  String message();

  default boolean isLoaded() {
    return state() == State.ANALYZING || state() == State.PAUSED;
  }
}
