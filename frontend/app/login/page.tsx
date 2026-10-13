"use client";
import Link from "next/link";
import {FormEvent,useState} from "react";
import {useRouter} from "next/navigation";
import {api} from "@/lib/api";

export default function Login(){
  const router=useRouter();
  const [identifier,setIdentifier]=useState(""),[password,setPassword]=useState(""),[busy,setBusy]=useState(false),[error,setError]=useState("");
  async function submit(e:FormEvent){
    e.preventDefault();setBusy(true);setError("");
    try{await api.login(identifier,password);router.push("/panel");router.refresh()}
    catch(x){setError(x instanceof Error?x.message:"ورود ناموفق بود")}
    finally{setBusy(false)}
  }
  return <div className="loginWrap"><div className="loginCard"><span className="eyebrow">ورود امن</span><h1>ورود به پنل</h1><p>با نام کاربری، شماره تلفن یا ایمیل وارد شوید.</p><form onSubmit={submit}>
    <label className="field"><span>نام کاربری / تلفن / ایمیل</span><input dir="ltr" required autoComplete="username" value={identifier} onChange={e=>setIdentifier(e.target.value)}/></label>
    <label className="field"><span>رمز عبور</span><input dir="ltr" type="password" required autoComplete="current-password" value={password} onChange={e=>setPassword(e.target.value)}/></label>
    {error&&<div className="errorBanner">{error}</div>}
    <button className="primaryButton" disabled={busy}>{busy?"در حال ورود…":"ورود"}</button>
    <Link className="linkButton" href="/register">مشتری جدید هستید؟ ساخت حساب</Link>
  </form></div></div>
}
