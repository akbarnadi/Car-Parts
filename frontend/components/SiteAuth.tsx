"use client";

import Link from "next/link";
import {usePathname,useRouter} from "next/navigation";
import {useEffect,useState} from "react";
import {api} from "@/lib/api";
import type {AuthMe} from "@/types/platform";

export default function SiteAuth(){
  const pathname=usePathname();
  const router=useRouter();
  const [me,setMe]=useState<AuthMe|null|undefined>(undefined);

  useEffect(()=>{
    let alive=true;
    api.me().then(v=>{if(alive)setMe(v)}).catch(()=>{if(alive)setMe(null)});
    return()=>{alive=false};
  },[pathname]);

  async function logout(){
    try{await api.logout()}finally{
      setMe(null);
      router.push("/");
      router.refresh();
    }
  }

  if(me===undefined)return <div className="navActions authSkeleton" aria-hidden="true"><span/><span/></div>;
  if(!me)return <div className="navActions"><Link href="/login" className="linkButton">ورود</Link><Link href="/register" className="primaryButton small">ساخت حساب</Link></div>;

  return <div className="navActions authLoggedIn">
    <Link href="/panel" className="accountPill"><span className="accountDot"/><span className="accountText"><b>{me.displayName}</b><small>@{me.username}</small></span></Link>
    <button type="button" className="logoutButton" onClick={logout} aria-label="خروج از حساب">خروج</button>
  </div>;
}
