package io.takamiya.mio.floating.wss;

public class WsTab {

    private final String name;
    private final Class<? extends WsBaseFragment> fragmentClass;

    public WsTab(String name, Class<? extends WsBaseFragment> fragmentClass) {
        this.name = name;
        this.fragmentClass = fragmentClass;
    }

    public String getName() {
        return name;
    }

    public Class<? extends WsBaseFragment> getFragmentClass() {
        return fragmentClass;
    }
}