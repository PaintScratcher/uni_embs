package embs;

public class SinkData {

    // 0 is FirstRX
    // 1 is SecondRX
    // 2 is FirstTX
    // 3 is NCalc
    // 4 is SecondTX

    protected int state = 0;
    protected byte firstRecieve;
    protected long NCalcFireTime;
    protected long t;
    protected int n;
}
