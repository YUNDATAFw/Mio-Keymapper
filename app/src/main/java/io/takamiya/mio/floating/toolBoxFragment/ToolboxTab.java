package io.takamiya.mio.floating.toolBoxFragment;

public class ToolboxTab {

    private final String name;
    private final Class<? extends ToolboxBaseFragment> fragmentClass;

    public ToolboxTab(String name, Class<? extends ToolboxBaseFragment> fragmentClass) {
        this.name = name;
        this.fragmentClass = fragmentClass;
    }

    public String getName() {
        return name;
    }

    public Class<? extends ToolboxBaseFragment> getFragmentClass() {
        return fragmentClass;
    }
}
