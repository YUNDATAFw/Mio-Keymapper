package io.takamiya.mio.config;

public class DuplicateConfiguration {
    private long intervalDuration = 40;
    private int numberOfRepetitions = 1;
    private int repeatTime = 0;
    private int stopRepeatingConditions = 0;

    public long getIntervalDuration() { return intervalDuration; }
    public void setIntervalDuration(long intervalDuration) { this.intervalDuration = intervalDuration; }

    public int getNumberOfRepetitions() { return numberOfRepetitions; }
    public void setNumberOfRepetitions(int numberOfRepetitions) { this.numberOfRepetitions = numberOfRepetitions; }

    public int getRepeatTime() { return repeatTime; }
    public void setRepeatTime(int repeatTime) { this.repeatTime = repeatTime; }

    public int getStopRepeatingConditions() { return stopRepeatingConditions; }
    public void setStopRepeatingConditions(int stopRepeatingConditions) { this.stopRepeatingConditions = stopRepeatingConditions; }
}
