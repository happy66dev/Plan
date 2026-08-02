import {createContext, PropsWithChildren, useCallback, useContext, useMemo, useState} from "react";

type NavigationContextValue = {
    currentTab: string | undefined,
    setCurrentTab: (tab: string) => void,
    lastUpdate: LastUpdate,
    updateRequested: number,
    updating: boolean,
    requestUpdate: () => void,
    finishUpdate: (date: number, formatted: string, isStillUpdating: boolean) => void,
    sidebarExpanded: boolean,
    setSidebarExpanded: (expanded: boolean) => void,
    toggleSidebar: () => void,
    sidebarItems: SidebarItem[],
    setSidebarItems: (sidebarItems: SidebarItem[]) => void,
    helpModalTopic: string | undefined,
    setHelpModalTopic: (topic: string | undefined) => void,
}

type LastUpdate = {
    date?: number;
    formatted?: string;
}

type SidebarItem = {
    name: string;
    href?: string;
    contents?: SidebarItem[];
    permission?: string;
}

const NavigationContext = createContext<NavigationContextValue | undefined>(undefined);

export const NavigationContextProvider = ({children}: PropsWithChildren) => {
    const [currentTab, setCurrentTab] = useState<string | undefined>(undefined);
    const [updateRequested, setUpdateRequested] = useState(Date.now());
    const [updating, setUpdating] = useState<boolean>(false);
    const [lastUpdate, setLastUpdate] = useState<LastUpdate>({});

    const [items, setItems] = useState<SidebarItem[]>([]);
    const [sidebarExpanded, setSidebarExpanded] = useState(window.innerWidth > 1350);
    const [helpModalTopic, setHelpModalTopic] = useState<string | undefined>(undefined);

    const setSidebarItems = useCallback((items: SidebarItem[]) => {
        const pathname = window.location.href;
        setItems(items);

        const isOpen = (item: SidebarItem) => {
            if (!item.href) return false;
            return '/' !== item.href && pathname.includes(item.href)
        }

        for (const item of items) {
            if (!item) continue;
            if (isOpen(item)) setCurrentTab(item.name);
            if (item.contents) {
                for (const subItem of item.contents) {
                    if (!subItem) continue;
                    if (isOpen(subItem)) setCurrentTab(subItem.name);
                }
            }
        }
    }, [setItems]);

    const requestUpdate = useCallback(() => {
        if (!updating) {
            setUpdateRequested(Date.now());
            setUpdating(true);
        }
    }, [updating, setUpdateRequested, setUpdating]);

    // TODO currently not possible due to extensionData getting updated off-tab
    // useEffect(requestUpdate, [currentTab]); // Force data to update when changing tab

    const finishUpdate = useCallback((date: number, formatted: string, isStillUpdating: boolean) => {
        // 喵~将接口时间转换为有限数字，防止异常响应污染刷新时间显示喵~
        const numericDate = Number(date);
        // 喵~只有有效的正数时间戳才更新顶部最后刷新时间喵~
        if (Number.isFinite(numericDate) && numericDate > 0) {
            setLastUpdate(previousLastUpdate => {
                // 喵~防御：旧响应不能覆盖更新的刷新时间喵~
                if (previousLastUpdate.date && numericDate <= previousLastUpdate.date) return previousLastUpdate;
                // 喵~保存经过校验的刷新时间，供 Header 显示喵~
                return {date: numericDate, formatted};
            });
        }
        // 喵~无论成功或失败都同步刷新状态，避免异常请求永久锁住刷新按钮喵~
        setUpdating(Boolean(isStillUpdating));
    }, [setLastUpdate, setUpdating]);

    const toggleSidebar = useCallback(() => {
        setSidebarExpanded(!sidebarExpanded);
    }, [setSidebarExpanded, sidebarExpanded])

    const sharedState = useMemo(() => {
        return {
            currentTab, setCurrentTab,
            lastUpdate, updateRequested, updating, requestUpdate, finishUpdate,
            sidebarExpanded, setSidebarExpanded, toggleSidebar, sidebarItems: items, setSidebarItems,
            helpModalTopic, setHelpModalTopic
        }
    }, [
        currentTab, setCurrentTab,
        lastUpdate, updateRequested, updating, requestUpdate, finishUpdate,
        sidebarExpanded, setSidebarExpanded, toggleSidebar, items, setSidebarItems,
        helpModalTopic, setHelpModalTopic
    ]);
    return (<NavigationContext.Provider value={sharedState}>
            {children}
        </NavigationContext.Provider>
    )
}

export const useNavigation = () => {
    const context = useContext(NavigationContext);
    if (!context) throw new Error("useNavigation has to be used within NavigationContextProvider");
    return context;
}