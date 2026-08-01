import React from 'react';
import {FontAwesomeIcon as Fa} from "@fortawesome/react-fontawesome";
import {faServer} from "@fortawesome/free-solid-svg-icons";
import {useTranslation} from "react-i18next";
import {Link} from "react-router";
import {useAuth} from "../../../hooks/authenticationHook.tsx";
import {useEmbed} from "../../../embed/EmbedContext";

const ServerPageLinkButton = ({uuid, className}) => {
    const {t} = useTranslation();
    // 喵~读取嵌入状态，嵌入玩家页不提供跳往服务器统计页面的入口喵~
    const {isEmbedMode} = useEmbed();
    const {hasPermission} = useAuth();

    const canSeeServer = hasPermission('access.server');
    // 喵~嵌入体验或无服务器权限时均不渲染服务器页面跳转按钮喵~
    if (isEmbedMode || !canSeeServer) return <></>

    return (
        <Link to={`/server/${uuid}`}
              className={`btn bg-servers ${className || ''}`}>
            <Fa icon={faServer}/> {t('html.label.serverPage')}
        </Link>
    )
};

export default ServerPageLinkButton