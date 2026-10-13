"use client";
import Link from "next/link";
import {useEffect,useState} from "react";
import {api} from "@/lib/api";
import {NotificationRow} from "@/types/platform";

export default function NotificationsPage(){
  const [items,setItems]=useState<NotificationRow[]>([]),[error,setError]=useState("");
  async function load(){try{setItems(await api.notifications(false))}catch(e){setError(e instanceof Error?e.message:"خطا")}}
  useEffect(()=>{load()},[]);
  async function read(n:NotificationRow){if(n.status!=="UNREAD")return;await api.readNotification(n.id);await load()}
  return <div className="pageShell"><div className="pageTitle"><span className="eyebrow">مرکز اعلان‌ها</span><h1>اعلان‌ها</h1><p>رویدادهای درخواست، قیمت، محصول، سفارش و ارسال در اینجا ثبت می‌شوند.</p></div>{error&&<div className="errorBanner">{error}</div>}<div className="tableCard">{items.map(n=><article key={n.id} style={{padding:16,borderBottom:"1px solid var(--line)",opacity:n.status==="READ"?.72:1}} onClick={()=>read(n)}><div style={{display:"flex",justifyContent:"space-between",gap:12}}><div><b>{n.title}</b><p>{n.body}</p><small className="muted">{new Date(n.createdAt).toLocaleString("fa-IR")} · {n.status==="UNREAD"?"خوانده‌نشده":"خوانده‌شده"}</small></div>{n.link&&<Link className="secondaryButton" href={n.link} onClick={()=>read(n)}>باز کردن</Link>}</div></article>)}{!items.length&&<div className="emptyInset">اعلانی وجود ندارد.</div>}</div></div>
}
