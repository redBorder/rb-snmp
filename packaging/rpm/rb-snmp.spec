Name:     rb-snmp
Version:  %{__version}
Release:  %{__release}%{?dist}

License:  GNU AGPLv3
URL:  https://github.com/redBorder/rb-snmp
Source0: %{name}-%{version}.tar.gz

BuildRequires: maven java-devel

Summary: redborder snmp service
Group: Services/Monitoring
Requires: java

%description
%{summary}

%prep
%setup -qn %{name}-%{version}

%build
mvn clean package

%install
mkdir -p %{buildroot}/usr/share/%{name}
install -D -m 644 target/rb-snmp-*-selfcontained.jar %{buildroot}/usr/share/%{name}

%clean
rm -rf %{buildroot}

%post -p /sbin/ldconfig
%postun -p /sbin/ldconfig

%files
%defattr(644,root,root)
/usr/share/%{name}/rb-snmp-*-selfcontained.jar

%changelog
* Wed Jun 15 2016 Carlos J. Mateos  <cjmateos@redborder.com> - 1.0.0-1
- first spec version
