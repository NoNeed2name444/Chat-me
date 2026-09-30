import itertools
APPLE=0.85; VAT=1.14; FX=52.0
US={'S_m':19.99,'S_y':149.0,'P_m':34.99,'P_y':299.0}
EG_EQ={'S_m':999.99,'S_y':7499.99,'P_m':1749.99,'P_y':14999.99}   # 999.99 observed (AMBOSS); others est. at ~50:1
EG_REG={'S_m':299.99,'S_y':1999.99,'P_m':599.99,'P_y':3999.99}
def eg_gross(e): return e/VAT/FX
def eg_net(e): return e/VAT*APPLE/FX
print("UNIT ECONOMICS")
for k in US:
    print(f"{k}: US gross {US[k]:.2f} net {US[k]*APPLE:.2f} | EG eq EGP {EG_EQ[k]:.2f} = ${EG_EQ[k]/FX:.2f} incl VAT, gross exVAT ${eg_gross(EG_EQ[k]):.2f}, net ${eg_net(EG_EQ[k]):.2f} | EG reg EGP {EG_REG[k]:.2f} = ${EG_REG[k]/FX:.2f}, gross ${eg_gross(EG_REG[k]):.2f}, net ${eg_net(EG_REG[k]):.2f}")
# AI cost per active Pro user: 15 turns/day x 22 days, 3k in + 0.5k out per turn
turns=15*22; tin=turns*3000/1e6; tout=turns*500/1e6
print(f"\nAI: turns/mo {turns}, Min {tin:.3f} Mout {tout:.3f}")
for name,pi,po in [("Opus 5.5",4,20),("Gemini 2.5 Flash paid",0.30,2.50),("Gemini 2.5 Flash-Lite paid",0.10,0.40),("Workers AI Llama 3.2 1B",0.027,0.201)]:
    c=tin*pi+tout*po; print(f"  {name}: ${c:.2f}/mo median, ${3*c:.2f} heavy(3x)")
n_in=0.027/0.011*1000; n_out=0.201/0.011*1000
per_turn=(3000*n_in+500*n_out)/1e6
print(f"Workers AI neurons per turn {per_turn:.1f}; turns/day on 10k free {10000/per_turn:.0f}; DAU served at 15 turns {10000/per_turn/15:.0f}")
for rpd in (250,500,1500): print(f"Gemini free {rpd} RPD -> DAU at 15 turns: {rpd/15:.0f}")

def run(signups0,growth,conv,annual,churn,egypt,pro=0.20,refund=0.03,regional=False,eg_mult=1.0,months=12,verbose=False):
    lag=(0.7,0.3); sign=[0]+[signups0*(1+growth)**m for m in range(months)]
    gross=[0]*(months+1); net=[0]*(months+1); mon_cohorts=[]  # (start_month, count, gross_pm, net_pm)
    actives_annual=0; payers_total=0; eg_payers=0
    for m in range(1,months+1):
        newp=conv*(lag[0]*sign[m]+lag[1]*sign[m-1])
        eg=newp*egypt*(eg_mult if regional else 1.0); intl=newp*(1-egypt)
        payers_total+=eg+intl; eg_payers+=eg
        for geo,cnt in (("eg",eg),("us",intl)):
            for tier,share in (("S",1-pro),("P",pro)):
                for plan,ps in (("y",annual),("m",1-annual)):
                    k=f"{tier}_{plan}"; n=cnt*share*ps
                    if geo=="us": g=US[k]; nt=US[k]*APPLE
                    else:
                        tab=EG_REG if regional else EG_EQ; g=eg_gross(tab[k]); nt=eg_net(tab[k])
                    if plan=="y":
                        gross[m]+=n*g*(1-refund); net[m]+=n*nt*(1-refund); actives_annual+=n*(1-refund)
                    else:
                        mon_cohorts.append((m,n,g,nt))
        for (s,n,g,nt) in mon_cohorts:
            surv=(1-churn)**(m-s); gross[m]+=n*surv*g; net[m]+=n*surv*nt
    act_m=sum(n*(1-churn)**(months-s) for (s,n,g,nt) in mon_cohorts)
    cum=list(itertools.accumulate(gross)); cumn=list(itertools.accumulate(net))
    res=dict(m1=gross[1],m1n=net[1],c3=cum[3],c6=cum[6],c12=cum[12],c12n=cumn[12],payers=payers_total,eg=eg_payers,
             act_m=act_m,act_y=actives_annual,g3=gross[3],g6=gross[6],g12=gross[12],signups=sum(sign))
    if verbose: print("  monthly gross:",[round(x) for x in gross[1:]])
    return res
S={"low":dict(signups0=400,growth=0.10,conv=0.015,annual=0.45,churn=0.15,egypt=0.90),
   "base":dict(signups0=1200,growth=0.15,conv=0.025,annual=0.60,churn=0.10,egypt=0.70),
   "high":dict(signups0=3000,growth=0.20,conv=0.045,annual=0.65,churn=0.07,egypt=0.50)}
print("\nSCENARIOS")
for name,p in S.items():
    r=run(**p,verbose=True)
    print(f"{name}: signups12 {r['signups']:.0f} payers {r['payers']:.0f} (eg {r['eg']:.0f}) | M1 gross {r['m1']:.0f} net {r['m1n']:.0f} | cum M3 {r['c3']:.0f} M6 {r['c6']:.0f} M12 {r['c12']:.0f} net12 {r['c12n']:.0f} | monthly gross M3 {r['g3']:.0f} M6 {r['g6']:.0f} M12 {r['g12']:.0f} | actives end: monthly {r['act_m']:.0f} annual {r['act_y']:.0f}")
print("\nREGIONAL PRICING VARIANTS (base)")
for mult in (1.0,2.0,3.0,4.0):
    r=run(**S["base"],regional=True,eg_mult=mult); print(f"  regional, Egypt conv x{mult}: M1 {r['m1']:.0f} cum12 {r['c12']:.0f} net12 {r['c12n']:.0f} payers {r['payers']:.0f}")
r=run(**{**S["base"],"egypt":0.0}); print(f"  base but 100% international: M1 {r['m1']:.0f} cum12 {r['c12']:.0f}")
r=run(**{**S["base"],"egypt":1.0}); print(f"  base but 100% Egypt equalised: M1 {r['m1']:.0f} cum12 {r['c12']:.0f}")
r=run(**{**S["base"],"egypt":1.0},regional=True,eg_mult=1.0); print(f"  base but 100% Egypt regional x1: M1 {r['m1']:.0f} cum12 {r['c12']:.0f}")
r=run(**{**S["base"],"egypt":1.0},regional=True,eg_mult=3.0); print(f"  base but 100% Egypt regional x3: M1 {r['m1']:.0f} cum12 {r['c12']:.0f}")
print("\nSENSITIVITY (12-mo cum gross, base = %.0f)"%run(**S["base"])['c12'])
b=S["base"]
tests={"signups0 -30/+30%":("signups0",0.7,1.3),"conv -30/+30%":("conv",0.7,1.3),"churn 7%/15%":("churn",7/10,15/10),"annual share 45%/75%":("annual",0.75,1.25),"egypt share 50%/90%":("egypt",50/70,90/70),"growth 5%/25%":("growth",5/15,25/15)}
for label,(k,lo,hi) in tests.items():
    rl=run(**{**b,k:b[k]*lo})['c12']; rh=run(**{**b,k:b[k]*hi})['c12']; print(f"  {label}: {rl:.0f} .. {rh:.0f} (swing {rh-rl:.0f})")
# price sensitivity: scale all prices
for f in (0.7,1.3):
    US2={k:v*f for k,v in US.items()}; EG2={k:v*f for k,v in EG_EQ.items()}
    saveU,saveE=dict(US),dict(EG_EQ); US.update(US2); EG_EQ.update(EG2); r=run(**b); US.update(saveU); EG_EQ.update(saveE); print(f"  price x{f}: {r['c12']:.0f}")
print("\nPAYERS NEEDED")
mix={"all annual Student US":149.0,"all monthly Student US":19.99,"base blend US":0.8*(0.6*149+0.4*19.99)+0.2*(0.6*299+0.4*34.99),
     "base blend Egypt equalised":0.8*(0.6*eg_gross(EG_EQ['S_y'])+0.4*eg_gross(EG_EQ['S_m']))+0.2*(0.6*eg_gross(EG_EQ['P_y'])+0.4*eg_gross(EG_EQ['P_m'])),
     "base blend Egypt regional":0.8*(0.6*eg_gross(EG_REG['S_y'])+0.4*eg_gross(EG_REG['S_m']))+0.2*(0.6*eg_gross(EG_REG['P_y'])+0.4*eg_gross(EG_REG['P_m']))}
for k,v in mix.items(): print(f"  {k}: ${v:.2f}/payer in M1 -> {4000/v:.0f} payers for $4,000")
# year-1 revenue per payer: annual once; monthly avg 4.7 payments (10% churn, mid-year conversion)
mp=(1-0.9**6)/0.1
for k,(y,m) in {"US Student":(149,19.99),"Egypt eq Student":(eg_gross(EG_EQ['S_y']),eg_gross(EG_EQ['S_m'])),"Egypt reg Student":(eg_gross(EG_REG['S_y']),eg_gross(EG_REG['S_m']))}.items():
    blend=0.6*y+0.4*m*mp; print(f"  {k}: yr-1 rev/payer ${blend:.0f} (60/40 mix) -> {60000/blend:.0f} payers for $60k; all-annual {60000/y:.0f}; all-monthly {60000/(m*mp):.0f}")
print(f"\nFixed/mo: Apple 99/12={99/12:.2f}, domain 10.44/12={10.44/12:.2f}, CF paid 5 conditional; total {99/12+10.44/12:.2f} / {99/12+10.44/12+5:.2f}")
